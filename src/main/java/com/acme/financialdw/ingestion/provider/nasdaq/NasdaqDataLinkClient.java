
package com.acme.financialdw.ingestion.provider.nasdaq;

import com.acme.financialdw.ingestion.config.IngestionProperties;
import com.acme.financialdw.ingestion.provider.FinancialDataProvider;
import com.acme.financialdw.ingestion.provider.RawRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.*;

/**
 * Fetches BITFINEX cryptocurrency data from Nasdaq Data Link (QDL).
 *
 * API docs: https://docs.data.nasdaq.com/docs/api-and-analysis-tools-for-tables-data
 *
 * Dataset path: /datasets/BITFINEX/{ticker}/data.json
 * AssetId format: "QDL/BITFINEX/{ticker}"
 *
 * The response envelope:
 * {
 *   "dataset_data": {
 *     "column_names": ["Date", "High", "Low", "Mid", "Last", "Bid", "Ask", "Volume"],
 *     "data": [["2024-01-01", 42000.0, ...], ...]
 *     "cursor_id": "abc123"   // null when no more pages
 *   }
 * }
 */
@Slf4j
@Component
public class NasdaqDataLinkClient implements FinancialDataProvider {

    private static final String PROVIDER_ID   = "BITFINEX";
    private static final String PROVIDER_NAME = "Nasdaq Data Link – Bitfinex";
    private static final String DB_CODE       = "BITFINEX";

    private final RestClient          restClient;
    private final IngestionProperties props;

    public NasdaqDataLinkClient(IngestionProperties props) {
        this.props = props;
        this.restClient = RestClient.builder()
                .baseUrl(props.getNasdaqBaseUrl())
                // Nasdaq Data Link sits behind an Incapsula/Imperva bot-wall that returns
                // 403 to clients with a non-browser User-Agent (e.g. the default
                // "Java-http-client/..."). Send a browser-like UA + JSON Accept header.
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Override public String getProviderId()   { return PROVIDER_ID; }
    @Override public String getProviderName() { return PROVIDER_NAME; }
    @Override public String getBaseUrl()      { return props.getNasdaqBaseUrl(); }

    @Override
    public Map<String, String> getProvenanceMetadata(String symbol) {
        return Map.of(
                "provider",   PROVIDER_NAME,
                "database",   DB_CODE,
                "dataset",    symbol,
                "assetId",    buildAssetId(symbol),
                "apiVersion", "v3"
        );
    }

    @Override
    public List<RawRecord> extract(String symbol, LocalDate from, LocalDate to) {
        String assetId = buildAssetId(symbol);
        List<RawRecord> result = new ArrayList<>();
        String cursorId = null;
        int page = 0;

        do {
            log.info("[Nasdaq] fetching {} page={} cursor={}", symbol, page++, cursorId);

            // Build URL
            StringBuilder url = new StringBuilder(
                    "/datasets/" + DB_CODE + "/" + symbol + "/data.json?api_key="
                            + props.getNasdaqApiKey()
                            + "&rows=" + props.getPageSize());
            if (from != null) url.append("&start_date=").append(from);
            if (to   != null) url.append("&end_date=").append(to);
            if (cursorId != null) url.append("&cursor_id=").append(cursorId);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.get()
                    .uri(url.toString())
                    .retrieve()
                    .body(Map.class);

            if (body == null) break;

            @SuppressWarnings("unchecked")
            Map<String, Object> datasetData = (Map<String, Object>) body.get("dataset_data");
            if (datasetData == null) break;

            @SuppressWarnings("unchecked")
            List<String> columns = (List<String>) datasetData.get("column_names");

            @SuppressWarnings("unchecked")
            List<List<Object>> rows = (List<List<Object>>) datasetData.get("data");

            if (rows != null) {
                for (List<Object> row : rows) {
                    RawRecord rec = parseRow(assetId, columns, row);
                    if (rec != null) result.add(rec);
                }
            }

            cursorId = (String) datasetData.get("cursor_id");

        } while (cursorId != null);

        log.info("[Nasdaq] extracted {} records for {}", result.size(), symbol);
        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String buildAssetId(String symbol) {
        return "QDL/" + DB_CODE + "/" + symbol;
    }

    private RawRecord parseRow(String assetId, List<String> columns, List<Object> row) {
        if (row == null || row.isEmpty()) return null;

        LocalDate date;
        try {
            date = LocalDate.parse(row.get(0).toString());
        } catch (Exception e) {
            log.warn("Skipping row with unparseable date: {}", row.get(0));
            return null;
        }

        Map<String, Object> indicators = new LinkedHashMap<>();
        for (int i = 1; i < columns.size() && i < row.size(); i++) {
            String key   = normaliseKey(columns.get(i));
            Object value = row.get(i);
            if (value != null) indicators.put(key, value);
        }

        return new RawRecord(assetId, date, indicators);
    }

    /** "Adj. Close" → "adjClose", "Last" → "last", etc. */
    private String normaliseKey(String raw) {
        String cleaned = raw.trim()
                .replace(".", "")
                .replace(" ", "_");
        // Convert to camelCase
        String[] parts = cleaned.split("[_\\-]");
        StringBuilder sb = new StringBuilder(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                sb.append(parts[i].substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }
}
