
package com.acme.financialdw.api;

import com.acme.financialdw.dal.AssetRepository;
import com.acme.financialdw.domain.FinancialAsset;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Lab 6 – /assets endpoints.
 *
 * GET /api/v1/assets
 *   → JSON array of assetId strings (paginated, offset/limit)
 *
 * GET /api/v1/assets/{assetId}   (assetId may contain slashes, e.g. QDL/BITFINEX/BTCUSD)
 *   → All versions of this asset (list of version objects, newest first)
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Assets", description = "Q1/Q2 – asset discovery and metadata")
public class AssetsController {

    private final AssetRepository assetRepo;

    /**
     * Q1 – list asset IDs.
     * Returns a flat JSON array of assetId strings, alphabetically sorted,
     * with user-defined pagination (offset + limit).
     */
    @GetMapping("/assets")
    @Operation(summary = "List available asset identifiers (Q1)",
               description = "Returns a flat JSON array of assetId strings. Supports offset/limit pagination.")
    public ResponseEntity<List<String>> listAssets(
            @Parameter(description = "Starting position (default 0)")
            @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "Maximum number of ids returned (default 20)")
            @RequestParam(defaultValue = "20") int limit) {

        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be 1–1000");
        if (offset < 0)                throw new IllegalArgumentException("offset must be >= 0");

        return ResponseEntity.ok(assetRepo.findAllAssetIds(offset, limit));
    }

    /**
     * Q2 – all versions for a given assetId.
     * assetId may contain slashes (e.g. QDL/BITFINEX/BTCUSD), so this mapping
     * uses a wildcard and extracts the remainder from the request URI.
     */
    @GetMapping("/assets/**")
    @Operation(summary = "Get all versions of an asset (Q2)",
               description = "Returns every stored version (different system_times) for this assetId, newest first.")
    public ResponseEntity<List<AssetVersionDto>> getAssetHistory(HttpServletRequest request) {
        String assetId = extractSuffix(request, "/api/v1/assets/");
        List<FinancialAsset> versions = assetRepo.findAll(assetId);
        if (versions.isEmpty()) throw new NoSuchElementException("Asset not found: " + assetId);
        return ResponseEntity.ok(versions.stream().map(AssetVersionDto::from).toList());
    }

    // ── DTO ───────────────────────────────────────────────────────────────

    public record AssetVersionDto(
            String id,
            String systemTime,
            boolean deleted,
            String assetClass,
            String symbol,
            String description,
            String region,
            java.util.Map<String, Object> attributes
    ) {
        static AssetVersionDto from(FinancialAsset a) {
            return new AssetVersionDto(
                    a.getAssetId(),
                    a.getSystemDate() != null ? a.getSystemDate().toString() : null,
                    a.isDeleted(),
                    a.getAssetClass() != null ? a.getAssetClass().name() : null,
                    a.getSymbol(),
                    a.getDescription(),
                    a.getRegion(),
                    a.getAttributes()
            );
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    static String extractSuffix(HttpServletRequest req, String prefix) {
        String uri = req.getRequestURI();
        int idx = uri.indexOf(prefix);
        if (idx < 0) throw new IllegalArgumentException("Cannot extract id from URI: " + uri);
        String raw = uri.substring(idx + prefix.length());
        // URL-decode in case client percent-encoded the slashes
        try {
            return java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return raw;
        }
    }
}
