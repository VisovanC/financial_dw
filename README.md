# Financial Data Warehouse

A data warehouse for financial-markets data (Acme Ltd lab project). It ingests crypto time-series
from an external provider, stores them in a **temporal, append-only** NoSQL model, and exposes them
through a REST API, Apache Spark analytics jobs, and an MCP server + LLM assistant.

## Tech stack
- **Java 21 / Spring Boot 4.0** — application + REST API
- **MongoDB 7** — storage (heterogeneous indicators, bi-temporal versioning)
- **Apache Spark 4.1** — aggregation + machine-learning jobs
- **MCP (JSON-RPC 2.0)** server + an LLM assistant (Anthropic) over the same data
- **Docker Compose** — runs everything locally

## What it does
- **Ingest** daily OHLCV candles from the **Bitfinex public API** (no key needed) via an ETL
  pipeline (extract → transform → load) that records data provenance.
- **Store** records append-only: nothing is updated or deleted in place — new versions are appended,
  and "deletes" are marker records, so any historical snapshot can be reproduced.
- **Serve** the data through a paginated REST API (list assets, asset details, data sources,
  time-series ranges).
- **Analyse** with Spark: a yearly aggregation job and a linear-regression price-prediction job,
  both writing results back into MongoDB.
- **Query in natural language** through an MCP server (tool access for any MCP client) and a built-in
  AI assistant endpoint.

## Prerequisites
- Docker Desktop (with Docker Compose)
- JDK 21 + Maven (only needed to build the Spark jobs JAR)

## Quick start
```bash
# 1. Configure environment
cp .env.example .env          # Windows: copy .env.example .env
#   ANTHROPIC_API_KEY is optional (only for the AI assistant); no Nasdaq key required.

# 2. Build the Spark analytics JAR (mounted into the Spark container)
cd spark-jobs && mvn -ntp clean package && cd ..

# 3. Start MongoDB + the app + Spark
docker compose up -d --build
```
The API comes up at **http://localhost:8080**. Swagger UI: **http://localhost:8080/swagger-ui.html**

> On Windows PowerShell use `curl.exe` (plain `curl` is an alias). For request bodies, write the JSON
> to a file first (`'{...}' | Out-File -Encoding ascii body.json`) and pass `--data "@body.json"`.

## Usage

### Ingest data
```bash
curl -X POST "http://localhost:8080/api/v1/ingestion/run" \
  -H "Content-Type: application/json" \
  -d '{"symbols":["BTCUSD","ETHUSD"],"from":"2023-01-01","to":"2024-12-31"}'
```
Returns per-symbol counters (`fetched / stored / skipped / failed`). Safe to re-run (idempotent).

### Explore via REST
```bash
curl "http://localhost:8080/api/v1/assets"                      # list asset ids (paginated: ?offset=&limit=)
curl "http://localhost:8080/api/v1/assets/QDL/BITFINEX/BTCUSD"  # all versions of one asset
curl "http://localhost:8080/api/v1/data-sources"                # list data sources
curl "http://localhost:8080/api/v1/data-sources/BITFINEX"       # one source's details
# Time-series for an asset+source over a half-open [start, end) interval, newest first:
curl "http://localhost:8080/api/v1/data?assetId=QDL/BITFINEX/BTCUSD&dataSourceId=BITFINEX&startBusinessDate=2024-01-01&endBusinessDate=2024-02-01&includeAttributes=true"
```

### Run the Spark analytics
```bash
# Aggregation (per asset/year: count, min, max, avg close) -> analytics_totals
docker exec financial-dw-spark /opt/spark/bin/spark-submit \
  --class com.acme.financialdw.spark.AggregationJob --master "local[*]" \
  --conf "spark.mongodb.read.connection.uri=mongodb://admin:secret@mongo:27017/financial_dw?authSource=admin" \
  --conf "spark.mongodb.write.connection.uri=mongodb://admin:secret@mongo:27017/financial_dw?authSource=admin" \
  /opt/spark-jobs/spark-jobs-1.0.0-shaded.jar

# Linear-regression price prediction -> regression_results / regression_predictions
#   (same command, change the class to com.acme.financialdw.spark.RegressionJob)

# Read results back through the API:
curl "http://localhost:8080/api/v1/spark/aggregation"
curl "http://localhost:8080/api/v1/spark/regression/results"
```

### MCP server (JSON-RPC 2.0)
```bash
curl "http://localhost:8080/mcp/tools"                          # browse available tools
curl -X POST "http://localhost:8080/mcp" -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"listAssets","arguments":{"offset":0,"limit":10}}}'
```
Tools: `listAssets`, `getAsset`, `listDataSources`, `getDataSource`, `getTimeSeries`, `getLatestPrice`.

### AI assistant (requires ANTHROPIC_API_KEY)
```bash
curl -X POST "http://localhost:8080/api/v1/assistant/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"What is the latest BTCUSD price and how did it trend in Jan 2024?"}'
```

## Tests
```bash
mvn -ntp test
```

## Stop / reset
```bash
docker compose down       # stop, keep data
docker compose down -v    # stop and wipe the MongoDB volume
```

## Notes
- Data source: the project originally targeted Nasdaq Data Link, but that free dataset has been
  decommissioned and is blocked, so ingestion uses the **Bitfinex public API** instead (no API key).
- Asset ids keep the `QDL/BITFINEX/<symbol>` format; data source id is `BITFINEX`.
