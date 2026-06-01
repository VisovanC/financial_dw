
package com.acme.financialdw.assistant;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "assistant")
public class AssistantProperties {
    private String anthropicApiKey = "";
    private String model = "claude-3-5-haiku-20241022";
    private int maxTokens = 4096;
    private String anthropicVersion = "2023-06-01";
    private String anthropicBaseUrl = "https://api.anthropic.com";
}
