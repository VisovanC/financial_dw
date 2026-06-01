
package com.acme.financialdw.ingestion;

import com.acme.financialdw.ingestion.pipeline.IngestionResult;
import com.acme.financialdw.ingestion.pipeline.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ingestion")
@RequiredArgsConstructor
@Tag(name = "Ingestion", description = "Trigger ETL runs for financial instruments")
public class IngestionController {

    private final IngestionService ingestionService;

    /**
     * Ingest one or more symbols.
     *
     * POST /api/v1/ingestion/run
     * {
     *   "symbols": ["BTCUSD", "ETHUSD"],
     *   "from": "2020-01-01",
     *   "to": "2024-12-31"
     * }
     */
    @PostMapping("/run")
    @Operation(summary = "Run ETL ingestion for a list of symbols")
    public ResponseEntity<List<IngestionResult>> run(@RequestBody IngestionRequest request) {
        List<IngestionResult> results = request.symbols().stream()
                .map(symbol -> ingestionService.ingest(symbol, request.from(), request.to()))
                .toList();
        return ResponseEntity.ok(results);
    }

    public record IngestionRequest(
            List<String> symbols,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {}
}
