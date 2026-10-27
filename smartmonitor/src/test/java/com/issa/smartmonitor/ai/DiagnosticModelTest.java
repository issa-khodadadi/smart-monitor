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
}
