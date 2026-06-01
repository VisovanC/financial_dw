
package com.acme.financialdw.api;

import com.acme.financialdw.dal.TimeSeriesRepository;
import com.acme.financialdw.dal.TimeSeriesRepository.PartitionKey;
import com.acme.financialdw.domain.TimeSeriesPoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * Lab 6 – /data endpoint (Q5).
 *
 * GET /api/v1/data
 *   ?assetId=QDL/BITFINEX/BTCUSD
 *   &dataSourceId=BITFINEX
 *   &startBusinessDate=2020-01-01
 *   &endBusinessDate=2021-01-01
 *   &includeAttributes=true
 *
 * Returns one record per businessDate (latest systemDate),
 * sorted newest-first (descending businessDate), non-deleted only.
 *
 * Response shape:
 * {
 *   "data": {
 *     "assetId": "...",
 *     "datasourceId": "...",
 *     "records": [
 *       {"businessDate": "2020-12-31", "values": {"close": 29000.0, ...}},
 *       ...
 *     ]
 *   },
 *   "attributes": ["close", "high", "low", ...]   // only when includeAttributes=true
 * }
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Time-Series Data", description = "Q5 – time-series retrieval")
public class DataController {

    private final TimeSeriesRepository tsRepo;

    @GetMapping("/data")
    @Operation(summary = "Retrieve time-series data for an asset/source pair (Q5)",
               description = "Returns the latest warehouse version of each businessDate in [startBusinessDate, endBusinessDate). "
                           + "Records are sorted newest-first. Pass includeAttributes=true to also receive the attribute list.")
    public ResponseEntity<TimeSeriesResponse> getData(

            @Parameter(description = "Asset identifier, e.g. QDL/BITFINEX/BTCUSD", required = true)
            @RequestParam String assetId,

            @Parameter(description = "Data source identifier, e.g. BITFINEX", required = true)
            @RequestParam String dataSourceId,

            @Parameter(description = "Start date inclusive (YYYY-MM-DD)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startBusinessDate,

            @Parameter(description = "End date exclusive (YYYY-MM-DD)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endBusinessDate,

            @Parameter(description = "Include attribute name list in response")
            @RequestParam(defaultValue = "false") boolean includeAttributes) {

        if (!startBusinessDate.isBefore(endBusinessDate)) {
            throw new IllegalArgumentException(
                    "startBusinessDate must be before endBusinessDate");
        }

        PartitionKey key = new PartitionKey(assetId, dataSourceId);
        List<TimeSeriesPoint> points = tsRepo.findTimeRange(key, startBusinessDate, endBusinessDate);

        // Build records list
        List<Map<String, Object>> records = points.stream()
                .map(p -> {
                    Map<String, Object> rec = new LinkedHashMap<>();
                    rec.put("businessDate", p.getBusinessDate().toString());
                    rec.put("values", p.getIndicators() != null ? p.getIndicators() : Map.of());
                    return rec;
                })
                .toList();

        // Collect all distinct attribute names (for includeAttributes)
        Set<String> attrSet = new LinkedHashSet<>();
        if (includeAttributes) {
            points.stream()
                    .filter(p -> p.getIndicators() != null)
                    .forEach(p -> attrSet.addAll(p.getIndicators().keySet()));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("assetId",      assetId);
        data.put("datasourceId", dataSourceId);
        data.put("records",      records);

        return ResponseEntity.ok(new TimeSeriesResponse(
                data,
                includeAttributes ? List.copyOf(attrSet) : null));
    }

    public record TimeSeriesResponse(
            Map<String, Object> data,
            List<String> attributes   // null when includeAttributes=false
    ) {}
}
