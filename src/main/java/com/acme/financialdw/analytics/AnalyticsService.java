
package com.acme.financialdw.analytics;

import com.acme.financialdw.dal.TimeSeriesRepository;
import com.acme.financialdw.dal.TimeSeriesRepository.PartitionKey;
import com.acme.financialdw.domain.TimeSeriesPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * In-app analytics (pure Java, no Spark).
 * Complement to the Spark jobs for on-demand REST analytics (Lab 7).
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final TimeSeriesRepository tsRepo;

    /** Basic descriptive statistics for the close price in [from, to). */
    public Map<String, Object> statistics(String assetId, String dataSourceId,
                                          LocalDate from, LocalDate to) {
        List<Double> closes = closes(assetId, dataSourceId, from, to);
        if (closes.isEmpty()) return Map.of("error", "no data");
        double mean = closes.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double min  = closes.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max  = closes.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double variance = closes.stream()
                .mapToDouble(v -> (v - mean) * (v - mean))
                .average().orElse(0);
        return Map.of("count", closes.size(), "mean", mean, "min", min,
                      "max", max, "stdDev", Math.sqrt(variance));
    }

    /** Simple moving average with the given window size. */
    public List<Map<String, Object>> movingAverage(String assetId, String dataSourceId,
                                                    LocalDate from, LocalDate to, int window) {
        List<TimeSeriesPoint> pts = tsRepo.findTimeRange(
                new PartitionKey(assetId, dataSourceId), from, to);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = window - 1; i < pts.size(); i++) {
            double avg = 0;
            for (int j = i - window + 1; j <= i; j++) {
                avg += toDouble(pts.get(j).getIndicators(), "close");
            }
            result.add(Map.of("businessDate", pts.get(i).getBusinessDate().toString(),
                              "sma", avg / window));
        }
        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private List<Double> closes(String assetId, String dsId, LocalDate from, LocalDate to) {
        return tsRepo.findTimeRange(new PartitionKey(assetId, dsId), from, to)
                .stream()
                .map(p -> toDouble(p.getIndicators(), "close"))
                .filter(v -> v != 0)
                .toList();
    }

    private double toDouble(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) return 0.0;
        Object v = map.get(key);
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }
}
