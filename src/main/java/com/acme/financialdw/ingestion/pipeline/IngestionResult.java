
package com.acme.financialdw.ingestion.pipeline;

/**
 * Observability counters returned after an ingestion run.
 */
public record IngestionResult(
        String symbol,
        long fetched,
        long stored,
        long skipped,
        long failed,
        String dataSourceId,
        String assetId
) {
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String symbol, dataSourceId, assetId;
        private long fetched, stored, skipped, failed;
        public Builder symbol(String v)       { symbol = v;       return this; }
        public Builder dataSourceId(String v) { dataSourceId = v; return this; }
        public Builder assetId(String v)      { assetId = v;      return this; }
        public Builder fetched(long v)        { fetched = v;      return this; }
        public Builder stored(long v)         { stored = v;       return this; }
        public Builder skipped(long v)        { skipped = v;      return this; }
        public Builder failed(long v)         { failed = v;       return this; }
        public IngestionResult build() {
            return new IngestionResult(symbol, fetched, stored, skipped, failed, dataSourceId, assetId);
        }
    }
}
