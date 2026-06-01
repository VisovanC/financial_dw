
package com.acme.financialdw.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Append-only asset record. Each change/creation appends a new document.
 * Partition key: assetId (e.g. "QDL/BITFINEX/BTCUSD").
 * Sort: systemDate DESC so the latest version is first.
 */
@Document(collection = "assets")
@CompoundIndex(name = "asset_partition_idx",
               def = "{'assetId': 1, 'systemDate': -1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialAsset {

    @Id
    private String id;

    /** Logical identifier, e.g. "QDL/BITFINEX/BTCUSD". */
    private String assetId;

    /** When this version was written to the warehouse. */
    private Instant systemDate;

    /** Soft-delete marker — never removed, a marker record is appended instead. */
    @Builder.Default
    private boolean deleted = false;

    private AssetClass assetClass;
    private String symbol;
    private String description;
    private String region;

    /** Flexible attribute bag — field names discovered from the provider. */
    private Map<String, Object> attributes;
}
