
package com.acme.financialdw.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Append-only time-series record (bi-temporal model).
 *
 * Partition key: (assetId, dataSourceId) — supports Q5 range queries.
 * Cluster order: businessDate ASC, systemDate DESC — latest version first per date.
 *
 * To delete a range: append a record with deleted=true.
 */
@Document(collection = "time_series")
@CompoundIndexes({
    @CompoundIndex(name = "ts_partition_idx",
                   def = "{'assetId': 1, 'dataSourceId': 1, 'businessDate': 1, 'systemDate': -1}"),
    @CompoundIndex(name = "ts_system_idx",
                   def = "{'assetId': 1, 'dataSourceId': 1, 'systemDate': -1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesPoint {

    @Id
    private String id;

    /** e.g. "QDL/BITFINEX/BTCUSD" */
    private String assetId;

    /** e.g. "BITFINEX" */
    private String dataSourceId;

    /** Valid / market date. */
    private LocalDate businessDate;

    /** When this version was stored in the warehouse. */
    private Instant systemDate;

    @Builder.Default
    private boolean deleted = false;

    /**
     * Flexible indicator bag: open, high, low, close, volume, etc.
     * Keys are normalised camelCase strings derived from provider column names.
     */
    private Map<String, Object> indicators;
}
