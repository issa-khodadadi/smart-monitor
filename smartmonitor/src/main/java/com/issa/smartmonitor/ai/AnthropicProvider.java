package com.issa.smartmonitor.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class AnthropicProvider implements AiAnalysisProvider {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();

    public AnthropicProvider(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.anthropic.com/v1")
                .build();
    }

    @Override
    public String analyze(String prompt) throws Exception {
        String requestBody = mapper.writeValueAsString(new MessageRequest(model, prompt));

        String response = restClient.post()
                .uri("/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        JsonNode root = mapper.readTree(response);
        JsonNode text = root.path("content").path(0).path("text");
        if (text.isMissingNode()) {
            throw new AiAnalysisException("Unexpected Anthropic response: " + response, null);
        }
        return text.asText();
    }

    private record MessageRequest(String model, int max_tokens, java.util.List<Message> messages) {
        MessageRequest(String model, String prompt) {
            this(model, 1500, java.util.List.of(new Message("user", prompt)));
        }
    }

    private record Message(String role, String content) {}
}