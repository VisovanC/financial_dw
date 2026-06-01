
package com.acme.financialdw.ingestion.pipeline;

import com.acme.financialdw.dal.AssetRepository;
import com.acme.financialdw.dal.DataSourceRepository;
import com.acme.financialdw.dal.TimeSeriesRepository;
import com.acme.financialdw.dal.TimeSeriesRepository.PartitionKey;
import com.acme.financialdw.domain.AssetClass;
import com.acme.financialdw.domain.DataSource;
import com.acme.financialdw.domain.FinancialAsset;
import com.acme.financialdw.domain.TimeSeriesPoint;
import com.acme.financialdw.ingestion.config.IngestionProperties;
import com.acme.financialdw.ingestion.provider.FinancialDataProvider;
import com.acme.financialdw.ingestion.provider.RawRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ETL orchestrator — Extract → Transform → Load.
 *
 * Design principles (from Lab 5 spec):
 * - Separation of concerns: each stage is isolated.
 * - Idempotent: re-running for the same symbol appends new versions, never corrupts.
 * - Batched writes: reduces MongoDB round-trips.
 * - Observability: per-run counters (fetched / stored / skipped / failed).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final AssetRepository      assetRepo;
    private final DataSourceRepository dsRepo;
    private final TimeSeriesRepository tsRepo;
    private final FinancialDataProvider provider;
    private final IngestionProperties   props;

    /**
     * Run a full ETL cycle for the given symbol.
     *
     * @param symbol ticker as understood by the provider, e.g. "BTCUSD"
     * @param from   earliest businessDate (inclusive), null = all available
     * @param to     latest businessDate (inclusive), null = today
     */
    public IngestionResult ingest(String symbol, LocalDate from, LocalDate to) {
        long fetched = 0, stored = 0, skipped = 0, failed = 0;

        // ── 1. Extract ─────────────────────────────────────────────────────
        List<RawRecord> records;
        try {
            records = provider.extract(symbol, from, to);
            fetched = records.size();
        } catch (Exception e) {
            log.error("[Ingestion] Extraction failed for {}: {}", symbol, e.getMessage());
            return IngestionResult.builder().symbol(symbol).failed(1).build();
        }

        if (records.isEmpty()) {
            log.warn("[Ingestion] No records returned for {}", symbol);
            return IngestionResult.builder().symbol(symbol).symbol(symbol).build();
        }

        // ── 2. Ensure DataSource exists ────────────────────────────────────
        String dataSourceId = provider.getProviderId();
        DataSource ds = ensureDataSource(dataSourceId, records);

        // ── 3. Ensure Asset exists ─────────────────────────────────────────
        String assetId = records.get(0).assetId();
        FinancialAsset asset = ensureAsset(assetId, symbol, ds);

        // ── 4. Load time-series in batches ─────────────────────────────────
        PartitionKey key = new PartitionKey(assetId, dataSourceId);
        List<TimeSeriesPoint> batch = new ArrayList<>(props.getBatchSize());

        for (RawRecord rec : records) {
            try {
                TimeSeriesPoint point = TimeSeriesPoint.builder()
                        .assetId(rec.assetId())
                        .dataSourceId(dataSourceId)
                        .businessDate(rec.businessDate())
                        .indicators(rec.indicators())
                        .build();
                batch.add(point);

                if (batch.size() >= props.getBatchSize()) {
                    stored += flushBatch(batch);
                    batch.clear();
                }
            } catch (Exception e) {
                log.warn("[Ingestion] Failed to transform record {}: {}", rec.businessDate(), e.getMessage());
                failed++;
            }
        }
        if (!batch.isEmpty()) stored += flushBatch(batch);

        skipped = fetched - stored - failed;
        log.info("[Ingestion] {} done: fetched={} stored={} skipped={} failed={}",
                symbol, fetched, stored, skipped, failed);

        return IngestionResult.builder()
                .symbol(symbol)
                .assetId(assetId)
                .dataSourceId(dataSourceId)
                .fetched(fetched)
                .stored(stored)
                .skipped(skipped)
                .failed(failed)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private DataSource ensureDataSource(String dataSourceId, List<RawRecord> records) {
        return dsRepo.findLatest(dataSourceId).orElseGet(() -> {
            Set<String> attrs = records.isEmpty()
                    ? new HashSet<>()
                    : new HashSet<>(records.get(0).indicators().keySet());
            DataSource ds = DataSource.builder()
                    .dataSourceId(dataSourceId)
                    .name(provider.getProviderName())
                    .description("Imported via " + provider.getProviderName())
                    .apiEndpoint(provider.getBaseUrl())
                    .attributes(attrs)
                    .provenance(provider.getProvenanceMetadata(
                            records.get(0).assetId().substring(
                                    records.get(0).assetId().lastIndexOf('/') + 1)))
                    .build();
            return dsRepo.save(ds);
        });
    }

    private FinancialAsset ensureAsset(String assetId, String symbol, DataSource ds) {
        return assetRepo.findLatest(assetId).orElseGet(() -> {
            FinancialAsset asset = FinancialAsset.builder()
                    .assetId(assetId)
                    .symbol(symbol)
                    .assetClass(AssetClass.CRYPTO)
                    .description(symbol + " from " + ds.getName())
                    .build();
            return assetRepo.save(asset);
        });
    }

    private long flushBatch(List<TimeSeriesPoint> batch) {
        try {
            // insertAll is not available on MongoTemplate directly for batch;
            // we use the DAL's save which does mongo.insert per record.
            // For true bulk, inject MongoTemplate and call insertAll.
            batch.forEach(tsRepo::save);
            return batch.size();
        } catch (Exception e) {
            log.error("[Ingestion] Batch flush failed: {}", e.getMessage());
            return 0;
        }
    }
}
