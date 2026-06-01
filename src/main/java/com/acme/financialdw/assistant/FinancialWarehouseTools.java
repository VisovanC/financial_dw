
package com.acme.financialdw.assistant;

import com.acme.financialdw.dal.AssetRepository;
import com.acme.financialdw.dal.DataSourceRepository;
import com.acme.financialdw.dal.TimeSeriesRepository;
import com.acme.financialdw.dal.TimeSeriesRepository.PartitionKey;
import com.acme.financialdw.domain.TimeSeriesPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Financial warehouse tools callable by the AI assistant and the MCP server.
 * Plain Spring service — no framework-specific tool annotations needed.
 */
@Service
@RequiredArgsConstructor
public class FinancialWarehouseTools {

    private final AssetRepository      assetRepo;
    private final DataSourceRepository dsRepo;
    private final TimeSeriesRepository tsRepo;

    public List<String> listAssets(int offset, int limit) {
        return assetRepo.findAllAssetIds(Math.max(0, offset), Math.min(100, limit));
    }

    public Map<String, Object> getAsset(String assetId) {
        return assetRepo.findLatest(assetId)
                .map(a -> Map.<String, Object>of(
                        "assetId",     a.getAssetId(),
                        "symbol",      a.getSymbol()      != null ? a.getSymbol()      : "",
                        "assetClass",  a.getAssetClass()  != null ? a.getAssetClass().name() : "",
                        "description", a.getDescription() != null ? a.getDescription() : "",
                        "systemDate",  a.getSystemDate()  != null ? a.getSystemDate().toString() : ""))
                .orElse(null);
    }

    public List<String> listDataSources(int offset, int limit) {
        return dsRepo.findAllDataSourceIds(Math.max(0, offset), Math.min(100, limit));
    }

    public Map<String, Object> getDataSource(String dataSourceId) {
        return dsRepo.findLatest(dataSourceId)
                .map(ds -> Map.<String, Object>of(
                        "dataSourceId", ds.getDataSourceId(),
                        "name",         ds.getName()        != null ? ds.getName()        : "",
                        "description",  ds.getDescription() != null ? ds.getDescription() : "",
                        "attributes",   ds.getAttributes()  != null ? ds.getAttributes()  : List.of()))
                .orElse(null);
    }

    public Map<String, Object> getTimeSeries(String assetId, String dataSourceId,
                                              String startDate, String endDate) {
        List<TimeSeriesPoint> points = tsRepo.findTimeRange(
                new PartitionKey(assetId, dataSourceId),
                LocalDate.parse(startDate), LocalDate.parse(endDate));

        List<Map<String, Object>> records = points.stream()
                .map(p -> Map.<String, Object>of(
                        "businessDate", p.getBusinessDate().toString(),
                        "values",       p.getIndicators() != null ? p.getIndicators() : Map.of()))
                .collect(Collectors.toList());

        return Map.of("assetId", assetId, "dataSourceId", dataSourceId,
                      "count", records.size(), "records", records);
    }

    public Map<String, Object> getLatestPrice(String assetId, String dataSourceId) {
        return tsRepo.findLatest(new PartitionKey(assetId, dataSourceId))
                .map(p -> Map.<String, Object>of(
                        "assetId",      assetId,
                        "dataSourceId", dataSourceId,
                        "businessDate", p.getBusinessDate().toString(),
                        "values",       p.getIndicators() != null ? p.getIndicators() : Map.of()))
                .orElse(Map.of("error", "No data found for " + assetId));
    }

    /** Dispatch a tool call by name. Used by both the MCP server and the assistant loop. */
    @SuppressWarnings("unchecked")
    public Object dispatch(String toolName, Map<String, Object> args) {
        return switch (toolName) {
            case "listAssets" -> listAssets(
                    intArg(args, "offset", 0), intArg(args, "limit", 20));
            case "getAsset" -> getAsset((String) args.get("assetId"));
            case "listDataSources" -> listDataSources(
                    intArg(args, "offset", 0), intArg(args, "limit", 20));
            case "getDataSource" -> getDataSource((String) args.get("dataSourceId"));
            case "getTimeSeries" -> getTimeSeries(
                    (String) args.get("assetId"),
                    (String) args.get("dataSourceId"),
                    (String) args.get("startDate"),
                    (String) args.get("endDate"));
            case "getLatestPrice" -> getLatestPrice(
                    (String) args.get("assetId"),
                    (String) args.get("dataSourceId"));
            default -> Map.of("error", "Unknown tool: " + toolName);
        };
    }

    private int intArg(Map<String, Object> args, String key, int def) {
        Object v = args.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }
}
