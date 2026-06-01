
package com.acme.financialdw.mcp;

import com.acme.financialdw.assistant.FinancialWarehouseTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * MCP (Model Context Protocol) server — JSON-RPC 2.0 over HTTP POST /mcp.
 *
 * Implements the three required MCP methods:
 *   initialize  — handshake, returns server capabilities
 *   tools/list  — returns the list of available tools
 *   tools/call  — executes a tool and returns the result
 *
 * Any MCP-compatible client (Claude Desktop, LangChain4j, etc.) can connect
 * by pointing to POST http://localhost:8080/mcp.
 *
 * Reference: https://modelcontextprotocol.io/specification
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "MCP Server", description = "Model Context Protocol server — JSON-RPC 2.0")
public class McpServer {

    private final FinancialWarehouseTools tools;
    private final ObjectMapper            mapper;

    // ── Tool descriptors ──────────────────────────────────────────────────

    private static final List<Map<String, Object>> TOOL_LIST = List.of(
        toolDesc("listAssets",
                 "List available financial asset identifiers with offset/limit pagination.",
                 Map.of("offset", strProp("Starting offset, default 0"),
                        "limit",  strProp("Max results, default 20"))),
        toolDesc("getAsset",
                 "Get metadata for an asset by its full id (e.g. QDL/BITFINEX/BTCUSD).",
                 Map.of("assetId", strProp("Full asset identifier"))),
        toolDesc("listDataSources",
                 "List available data source identifiers.",
                 Map.of("offset", strProp("Starting offset, default 0"),
                        "limit",  strProp("Max results, default 20"))),
        toolDesc("getDataSource",
                 "Get details for a data source (e.g. BITFINEX).",
                 Map.of("dataSourceId", strProp("Data source identifier"))),
        toolDesc("getTimeSeries",
                 "Get daily prices in [startDate, endDate) for an asset. Dates: YYYY-MM-DD.",
                 Map.of("assetId",      strProp("Asset id"),
                        "dataSourceId", strProp("Data source id"),
                        "startDate",    strProp("Start date (inclusive)"),
                        "endDate",      strProp("End date (exclusive)"))),
        toolDesc("getLatestPrice",
                 "Get the most recent price record for an asset.",
                 Map.of("assetId",      strProp("Asset id"),
                        "dataSourceId", strProp("Data source id")))
    );

    // ── MCP endpoint ──────────────────────────────────────────────────────

    @PostMapping("/mcp")
    @Operation(summary = "MCP JSON-RPC 2.0 endpoint")
    public ResponseEntity<Map<String, Object>> handleMcp(
            @RequestBody Map<String, Object> request) {

        String id     = String.valueOf(request.getOrDefault("id", "1"));
        String method = (String) request.get("method");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", Map.of());

        log.debug("[MCP] method={} id={}", method, id);

        try {
            Object result = switch (method != null ? method : "") {
                case "initialize"  -> handleInitialize();
                case "tools/list"  -> Map.of("tools", TOOL_LIST);
                case "tools/call"  -> handleToolCall(params);
                default -> throw new IllegalArgumentException("Unknown method: " + method);
            };
            return ResponseEntity.ok(jsonRpcSuccess(id, result));
        } catch (Exception e) {
            log.error("[MCP] error handling method={}: {}", method, e.getMessage());
            return ResponseEntity.ok(jsonRpcError(id, -32603, e.getMessage()));
        }
    }

    /** Browse available tools (convenience GET endpoint). */
    @GetMapping("/mcp/tools")
    @Operation(summary = "Browse MCP tools (read-only)")
    public ResponseEntity<List<Map<String, Object>>> listTools() {
        return ResponseEntity.ok(TOOL_LIST);
    }

    // ── Method handlers ───────────────────────────────────────────────────

    private Map<String, Object> handleInitialize() {
        return Map.of(
                "protocolVersion", "2024-11-05",
                "serverInfo",      Map.of("name", "financial-dw-mcp", "version", "1.0.0"),
                "capabilities",    Map.of("tools", Map.of("listChanged", false)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolCall(Map<String, Object> params) throws Exception {
        String toolName = (String) params.get("name");
        Map<String, Object> args = (Map<String, Object>) params.getOrDefault("arguments", Map.of());

        log.info("[MCP] tools/call name={} args={}", toolName, args);
        Object result = tools.dispatch(toolName, args);
        String json   = mapper.writeValueAsString(result);

        return Map.of("content", List.of(Map.of("type", "text", "text", json)));
    }

    // ── JSON-RPC 2.0 helpers ──────────────────────────────────────────────

    private static Map<String, Object> jsonRpcSuccess(String id, Object result) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id",      id);
        resp.put("result",  result);
        return resp;
    }

    private static Map<String, Object> jsonRpcError(String id, int code, String message) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id",      id);
        resp.put("error",   Map.of("code", code, "message", message));
        return resp;
    }

    private static Map<String, Object> toolDesc(String name, String desc,
                                                  Map<String, Object> props) {
        return Map.of(
                "name",        name,
                "description", desc,
                "inputSchema", Map.of("type", "object", "properties", props));
    }

    private static Map<String, Object> strProp(String description) {
        return Map.of("type", "string", "description", description);
    }
}
