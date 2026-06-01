package com.acme.financialdw.ingestion.provider.bitfinex;

import com.acme.financialdw.ingestion.provider.FinancialDataProvider;
import com.acme.financialdw.ingestion.provider.RawRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches daily OHLCV candles from Bitfinex's free public REST API.
 *
 * <p>Used as the platform's live data provider because Nasdaq Data Link's legacy
 * Bitfinex dataset (data.nasdaq.com/api/v3/datasets/BITFINEX/...) has been
 * decommissioned and is hard-blocked by an Imperva/Incapsula security service.
 * The Bitfinex public API needs no API key and is not behind a bot-wall.
 *
 * <p>Endpoint (candles):
 * <pre>
 *   GET https://api-pub.bitfinex.com/v2/candles/trade:1D:t{SYMBOL}/hist?limit=10000&sort=1
 * </pre>
 * Response is a JSON array of arrays, one per candle:
 * <pre>
 *   [ [ MTS, OPEN, CLOSE, HIGH, LOW, VOLUME ], ... ]   // MTS = epoch millis
 * </pre>
 *
 * <p>Symbols are passed as plain pairs (e.g. {@code BTCUSD}); this client prefixes
 * them with {@code t} as Bitfinex requires (e.g. {@code tBTCUSD}). AssetId keeps the
 * existing {@code QDL/BITFINEX/{symbol}} shape so the rest of the platform is unchanged.
 *
 * Docs: https://docs.bitfinex.com/reference/rest-public-candles
 */
@Slf4j
@Component
@Primary
public class BitfinexPublicClient implements FinancialDataProvider {

    private static final String PROVIDER_ID   = "BITFINEX";
    private static final String PROVIDER_NAME = "Bitfinex Public API";
    private static final String BASE_URL      = "https://api-pub.bitfinex.com";
    private static final int    MAX_CANDLES   = 10000;

    private final RestClient restClient;

    public BitfinexPublicClient() {
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Override public String getProviderId()   { return PROVIDER_ID; }
    @Override public String getProviderName() { return PROVIDER_NAME; }
    @Override public String getBaseUrl()      { return BASE_URL; }

    @Override
    public Map<String, String> getProvenanceMetadata(String symbol) {
        return Map.of(
                "provider",   PROVIDER_NAME,
                "database",   PROVIDER_ID,
                "dataset",    symbol,
                "assetId",    buildAssetId(symbol),
                "apiVersion", "v2",
                "endpoint",   "/v2/candles/trade:1D:t" + symbol + "/hist"
        );
    }

    @Override
    public List<RawRecord> extract(String symbol, LocalDate from, LocalDate to) {
        String assetId = buildAssetId(symbol);

        StringBuilder uri = new StringBuilder("/v2/candles/trade:1D:t")
                .append(symbol)
                .append("/hist?limit=").append(MAX_CANDLES)
                .append("&sort=1");                 // ascending by time
        if (from != null) uri.append("&start=").append(toEpochMillis(from, false));
        if (to   != null) uri.append("&end=").append(toEpochMillis(to, true));

        log.info("[Bitfinex] fetching {} ({})", symbol, uri);

        @SuppressWarnings("unchecked")
        List<List<Object>> candles = restClient.get()
                .uri(uri.toString())
                .retrieve()
                .body(List.class);

        List<RawRecord> result = new ArrayList<>();
        if (candles == null) {
            log.warn("[Bitfinex] empty response for {}", symbol);
            return result;
        }

        for (List<Object> c : candles) {
            RawRecord rec = parseCandle(assetId, c);
            if (rec != null) result.add(rec);
        }

        log.info("[Bitfinex] extracted {} records for {}", result.size(), symbol);
        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String buildAssetId(String symbol) {
        return "QDL/" + PROVIDER_ID + "/" + symbol;
    }

    /** Candle row: [ MTS, OPEN, CLOSE, HIGH, LOW, VOLUME ]. */
    private RawRecord parseCandle(String assetId, List<Object> c) {
        if (c == null || c.size() < 6) return null;
        try {
            long mts = ((Number) c.get(0)).longValue();
            LocalDate businessDate = Instant.ofEpochMilli(mts)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate();

            Map<String, Object> indicators = new LinkedHashMap<>();
            indicators.put("open",   asDouble(c.get(1)));
            indicators.put("close",  asDouble(c.get(2)));
            indicators.put("high",   asDouble(c.get(3)));
            indicators.put("low",    asDouble(c.get(4)));
            indicators.put("volume", asDouble(c.get(5)));

            return new RawRecord(assetId, businessDate, indicators);
        } catch (Exception e) {
            log.warn("[Bitfinex] skipping unparseable candle {}: {}", c, e.getMessage());
            return null;
        }
    }

    private Double asDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }

    private long toEpochMillis(LocalDate date, boolean endOfDay) {
        return (endOfDay
                ? date.atTime(23, 59, 59).toInstant(ZoneOffset.UTC)
                : date.atStartOfDay().toInstant(ZoneOffset.UTC))
                .toEpochMilli();
    }
}
