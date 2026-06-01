
package com.acme.financialdw.api;

import com.acme.financialdw.dal.DataSourceRepository;
import com.acme.financialdw.domain.DataSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Lab 6 – /data-sources endpoints.
 *
 * GET /api/v1/data-sources              → array of dataSourceId strings
 * GET /api/v1/data-sources/{id}         → all versions of this data source
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Data Sources", description = "Q3/Q4 – data source discovery and metadata")
public class DataSourcesController {

    private final DataSourceRepository dsRepo;

    @GetMapping("/data-sources")
    @Operation(summary = "List available data source identifiers (Q3)")
    public ResponseEntity<List<String>> listDataSources(
            @RequestParam(defaultValue = "0")  int offset,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(dsRepo.findAllDataSourceIds(offset, limit));
    }

    @GetMapping("/data-sources/**")
    @Operation(summary = "Get all versions of a data source (Q4)")
    public ResponseEntity<List<DataSourceVersionDto>> getDataSourceHistory(HttpServletRequest request) {
        String dsId = AssetsController.extractSuffix(request, "/api/v1/data-sources/");
        List<DataSource> versions = dsRepo.findAll(dsId);
        if (versions.isEmpty()) throw new NoSuchElementException("DataSource not found: " + dsId);
        return ResponseEntity.ok(versions.stream().map(DataSourceVersionDto::from).toList());
    }

    public record DataSourceVersionDto(
            String id,
            String systemTime,
            boolean deleted,
            String name,
            String description,
            String apiEndpoint,
            java.util.Set<String> attributes,
            java.util.Map<String, String> provenance
    ) {
        static DataSourceVersionDto from(DataSource ds) {
            return new DataSourceVersionDto(
                    ds.getDataSourceId(),
                    ds.getSystemDate() != null ? ds.getSystemDate().toString() : null,
                    ds.isDeleted(),
                    ds.getName(),
                    ds.getDescription(),
                    ds.getApiEndpoint(),
                    ds.getAttributes(),
                    ds.getProvenance()
            );
        }
    }
}
