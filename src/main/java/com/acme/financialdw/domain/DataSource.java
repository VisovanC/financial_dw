
package com.acme.financialdw.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Set;

/**
 * Append-only data-source record.
 * dataSourceId identifies the provider, e.g. "BITFINEX".
 */
@Document(collection = "data_sources")
@CompoundIndex(name = "ds_partition_idx",
               def = "{'dataSourceId': 1, 'systemDate': -1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSource {

    @Id
    private String id;

    /** Provider identifier, e.g. "BITFINEX". */
    private String dataSourceId;

    private Instant systemDate;

    @Builder.Default
    private boolean deleted = false;

    private String name;
    private String description;
    private String apiEndpoint;

    /** Attribute (column) names discovered from the first ingestion. */
    private Set<String> attributes;

    /** Provenance metadata: db code, dataset, parameters, etc. */
    private java.util.Map<String, String> provenance;
}
