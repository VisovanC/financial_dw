
package com.acme.financialdw.ingestion.provider;

import java.time.LocalDate;
import java.util.Map;

/**
 * A single normalised row returned by any provider.
 *
 * @param assetId      logical warehouse id, e.g. "QDL/BITFINEX/BTCUSD"
 * @param businessDate the market / valid date of this measurement
 * @param indicators   normalised k/v indicators (camelCase keys)
 */
public record RawRecord(String assetId, LocalDate businessDate, Map<String, Object> indicators) {}
