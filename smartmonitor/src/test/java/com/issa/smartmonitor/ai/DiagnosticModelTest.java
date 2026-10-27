package com.issa.smartmonitor.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticModelTest {

    @Test
    void holdoutFixturesKeepTheirLabels() throws Exception {
        try (InputStream in = DiagnosticModelTest.class.getResourceAsStream("/models/diagnostic/model.json")) {
            assertNotNull(in);
            DiagnosticModel model = DiagnosticModel.load(in, new ObjectMapper());
            assertTrue(model.holdoutAccuracy() >= 0.93, "holdout accuracy " + model.holdoutAccuracy());
            for (JsonNode fixture : model.fixtures()) {
                Map<String, Double> raw = new HashMap<>();
                fixture.get("raw").fields().forEachRemaining(entry -> raw.put(entry.getKey(), entry.getValue().asDouble()));
                DiagnosticModel.Prediction prediction = model.predict(raw);
                assertEquals(fixture.get("label").asText(), prediction.label());
                assertTrue(prediction.confidence() >= 0.7, fixture.get("label").asText() + " confidence " + prediction.confidence());
            }
        }
    }

    @Test
    void failingSlowControllerIsNotCalledMemoryPressure() throws Exception {
        try (InputStream in = DiagnosticModelTest.class.getResourceAsStream("/models/diagnostic/model.json")) {
            DiagnosticModel model = DiagnosticModel.load(in, new ObjectMapper());
            Map<String, Double> raw = new HashMap<>();
            raw.put("calls", 2.0);
            raw.put("avg_ms", 7694.0);
            raw.put("p95_ms", 9620.0);
            raw.put("p99_ms", 9620.0);
            raw.put("max_ms", 9620.0);
            raw.put("db_ratio", 0.0);
            raw.put("error_rate", 1.0);
            raw.put("calls_per_request", 1.0);
            raw.put("self_ratio", 0.0008);
            raw.put("memory_kb", 85372.0);
            raw.put("fragmented", 0.0);
            raw.put("repository_share", 0.0);
            raw.put("errors", 2.0);
            DiagnosticModel.Prediction prediction = model.predict(raw);
            assertEquals("ERROR_BURST", prediction.label());
        }
    }
}
