package com.issa.smartmonitor.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class OllamaProvider implements AiAnalysisProvider {

    private final RestClient restClient;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();

    public OllamaProvider(String baseUrl, String model) {
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public String analyze(String prompt) throws Exception {
        String requestBody = mapper.writeValueAsString(new GenerateRequest(model, prompt, false));

        String response = restClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        JsonNode root = mapper.readTree(response);
        JsonNode text = root.path("response");
        if (text.isMissingNode()) {
            throw new AiAnalysisException("Unexpected Ollama response: " + response, null);
        }
        return text.asText();
    }

    private record GenerateRequest(String model, String prompt, boolean stream) {}
}