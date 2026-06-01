
package com.acme.financialdw.ingestion.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ingestion")
public class IngestionProperties {
    private String nasdaqApiKey = "DEMO_KEY";
    private String nasdaqBaseUrl = "https://data.nasdaq.com/api/v3";
    /** Max rows per Nasdaq API page request. */
    private int pageSize = 100;
    /** MongoDB bulk-insert batch size. */
    private int batchSize = 50;
}
