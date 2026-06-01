package com.acme.financialdw.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * AI assistant endpoint.
 *
 * Calls the Anthropic Messages API directly via RestClient (no Spring AI needed).
 * Implements an agentic tool loop: Claude calls tools → we execute them → feed
 * results back → repeat until Claude returns a final text answer.
 *
 * POST /api/v1/assistant/chat
 * { "message": "What is the latest Bitcoin price?" }
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/assistant")
@Tag(name = "AI Assistant", description = "LLM assistant with tool access to the warehouse")
public class AssistantController {

    private static final String ANTHROPIC_API = "https://api.anthropic.com";
    private static final int    MAX_ITERATIONS = 5;

    private final AssistantProperties   props;
    private final FinancialWarehouseTools tools;
    private final RestClient            http;
    private final ObjectMapper          mapper;

    // ── Tool definitions sent to Claude ───────────────────────────────────
    private static final List<Map<String, Object>> TOOLS = List.of(
        tool("listAssets",
             "List available financial asset identifiers (e.g. QDL/BITFINEX/BTCUSD). Supports offset/limit pagination.",
             Map.of("offset", Map.of("type","integer","description","Start position, default 0"),
                    "limit",  Map.of("type","integer","description","Max results, default 20"))),
        tool("getAsset",
             "Get metadata for a specific asset by its full identifier.",
             Map.of("assetId", Map.of("type","string","description","Full asset id, e.g. QDL/BITFINEX/BTCUSD"))),
        tool("listDataSources",
             "List available data source identifiers.",
             Map.of("offset", Map.of("type","integer","description","Start position, default 0"),
                    "limit",  Map.of("type","integer","description","Max results, default 20"))),
        tool("getDataSource",
             "Get details for a specific data source.",
             Map.of("dataSourceId", Map.of("type","string","description","Data source id, e.g. BITFINEX"))),
        tool("getTimeSeries",
             "Get daily prices for an asset in a date range [startDate, endDate). Dates: YYYY-MM-DD.",
             Map.of("assetId",      Map.of("type","string"),
                    "dataSourceId", Map.of("type","string"),
                    "startDate",    Map.of("type","string","description","ISO-8601 start date"),
                    "endDate",      Map.of("type","string","description","ISO-8601 end date (exclusive)"))),
        tool("getLatestPrice",
             "Get the most recent price record for an asset.",
             Map.of("assetId",      Map.of("type","string"),
                    "dataSourceId", Map.of("type","string")))
    );

    public AssistantController(AssistantProperties props, FinancialWarehouseTools tools,
                                ObjectMapper mapper) {
        this.props  = props;
        this.tools  = tools;
        this.mapper = mapper;
        this.http   = RestClient.builder()
                .baseUrl(ANTHROPIC_API)
                .defaultHeader("x-api-key",          props.getAnthropicApiKey())
                .defaultHeader("anthropic-version",   props.getAnthropicVersion())
                .defaultHeader("content-type",        "application/json")
                .build();
    }

    @PostMapping("/chat")
    @Operation(summary = "Chat with the financial data warehouse AI assistant")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        if (props.getAnthropicApiKey() == null || props.getAnthropicApiKey().isBlank()) {
            return new ChatResponse("AI assistant is not configured (ANTHROPIC_API_KEY not set).", null);
        }
        try {
            return new ChatResponse(runAgenticLoop(request.message()), null);
        } catch (Exception e) {
            log.error("[Assistant] error", e);
            return new ChatResponse(null, e.getMessage());
        }
    }

    // ── Agentic tool loop ─────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String runAgenticLoop(String userMessage) throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", userMessage));

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model",      props.getModel());
            body.put("max_tokens", props.getMaxTokens());
            body.put("system",     "You are a helpful financial data warehouse assistant for Acme Ltd. "
                    + "Always use your tools to fetch real data before answering. Be concise and precise.");
            body.put("tools",    TOOLS);
            body.put("messages", messages);

            String responseJson = http.post()
                    .uri("/v1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);

            Map<String, Object> response = mapper.readValue(responseJson, Map.class);
            String stopReason = (String) response.get("stop_reason");
            List<Map<String, Object>> contentBlocks = (List<Map<String, Object>>) response.get("content");

            // Add assistant message to history
            messages.add(Map.of("role", "assistant", "content", contentBlocks));

            if ("end_turn".equals(stopReason)) {
                // Final answer — extract text
                return contentBlocks.stream()
                        .filter(b -> "text".equals(b.get("type")))
                        .map(b -> (String) b.get("text"))
                        .findFirst()
                        .orElse("(no response)");
            }

            if ("tool_use".equals(stopReason)) {
                // Execute all tool calls
                List<Map<String, Object>> toolResults = new ArrayList<>();
                for (Map<String, Object> block : contentBlocks) {
                    if ("tool_use".equals(block.get("type"))) {
                        String toolName = (String) block.get("name");
                        String toolUseId = (String) block.get("id");
                        Map<String, Object> toolArgs = (Map<String, Object>) block.get("input");

                        log.info("[Assistant] calling tool: {} args={}", toolName, toolArgs);
                        Object result = tools.dispatch(toolName, toolArgs != null ? toolArgs : Map.of());
                        String resultJson = mapper.writeValueAsString(result);

                        toolResults.add(Map.of(
                                "type",        "tool_result",
                                "tool_use_id", toolUseId,
                                "content",     resultJson));
                    }
                }
                messages.add(Map.of("role", "user", "content", toolResults));
            }
        }
        return "Max iterations reached without a final answer.";
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static Map<String, Object> tool(String name, String description,
                                             Map<String, Object> properties) {
        return Map.of(
                "name",         name,
                "description",  description,
                "input_schema", Map.of(
                        "type",       "object",
                        "properties", properties));
    }

    public record ChatRequest(@NotBlank @Size(max = 2000) String message) {}
    public record ChatResponse(String answer, String error) {}
}
