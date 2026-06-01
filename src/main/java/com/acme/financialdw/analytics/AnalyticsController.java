
package com.acme.financialdw.analytics;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "In-app analytics (Lab 7 complement to Spark jobs)")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> statistics(
            @RequestParam String assetId,
            @RequestParam String dataSourceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(analyticsService.statistics(assetId, dataSourceId, from, to));
    }

    @GetMapping("/moving-average")
    public ResponseEntity<List<Map<String, Object>>> movingAverage(
            @RequestParam String assetId,
            @RequestParam String dataSourceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "20") int window) {
        return ResponseEntity.ok(analyticsService.movingAverage(assetId, dataSourceId, from, to, window));
    }
}
