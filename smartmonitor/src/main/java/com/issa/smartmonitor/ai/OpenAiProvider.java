package com.issa.smartmonitor.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class OpenAiProvider implements AiAnalysisProvider {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiProvider(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .build();
    }

    @Override
    public String analyze(String prompt) throws Exception {
        String requestBody = mapper.writeValueAsString(new ChatRequest(model, prompt));

        String response = restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        JsonNode root = mapper.readTree(response);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode()) {
            throw new AiAnalysisException("Unexpected OpenAI response: " + response, null);
        }
        return content.asText();
    }

    private record ChatRequest(String model, java.util.List<Message> messages, double temperature) {
        ChatRequest(String model, String prompt) {
            this(model, java.util.List.of(new Message("user", prompt)), 0.2);
        }
    }

    private record Message(String role, String content) {}
}