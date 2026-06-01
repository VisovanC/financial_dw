
package com.acme.financialdw.ingestion.provider;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Provider-agnostic extraction interface.
 * Implementations talk to external APIs; callers see only RawRecords.
 */
public interface FinancialDataProvider {

    /** Stable provider identifier stored in DataSource.dataSourceId. */
    String getProviderId();

    String getProviderName();

    String getBaseUrl();

    /** Provenance metadata written to DataSource.provenance. */
    Map<String, String> getProvenanceMetadata(String symbol);

    /**
     * Fetch all pages for the given symbol between [from, to].
     * The provider handles pagination internally.
     *
     * @param symbol ticker / dataset name as known by this provider, e.g. "BTCUSD"
     */
    List<RawRecord> extract(String symbol, LocalDate from, LocalDate to);
}
