package com.issa.smartmonitor.ai;

import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.ModelSupport;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.safetensors.prompt.PromptContext;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public class JlamaProvider implements AiAnalysisProvider {

    private static final String BUNDLED_MODEL_RESOURCE_PATH = "models/tuned";
    private static final String[] MODEL_FILES = {
            "config.json", "tokenizer.json", "tokenizer_config.json", "model.safetensors"
    };

    private final String workingDirectory;
    private volatile AbstractModel model;

    public JlamaProvider(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    private synchronized AbstractModel getOrLoadModel() {
        if (model == null) {
            try {
                File localModelPath = extractBundledModel();
                model = ModelSupport.loadModel(localModelPath, DType.F32, DType.I8);
            } catch (Exception e) {
                Throwable root = e;
                while (root.getCause() != null) {
                    root = root.getCause();
                }
                throw new AiAnalysisException(
                        "Failed to load bundled fine-tuned model: " + e.getMessage() +
                                " | Root cause: " + root.getClass().getName() + ": " + root.getMessage(),
                        e
                );
            }
        }
        return model;
    }

    private File extractBundledModel() throws Exception {
        ClassPathResource marker = new ClassPathResource(BUNDLED_MODEL_RESOURCE_PATH + "/config.json");
        if (!marker.exists()) {
            throw new AiAnalysisException("Bundled model not found on classpath at " + BUNDLED_MODEL_RESOURCE_PATH, null);
        }

        File targetDir = new File(workingDirectory, "bundled-tuned-model");
        targetDir.mkdirs();

        for (String fileName : MODEL_FILES) {
            ClassPathResource resource = new ClassPathResource(BUNDLED_MODEL_RESOURCE_PATH + "/" + fileName);
            if (!resource.exists()) continue; // tokenizer_config.json etc. may not always be present

            File targetFile = new File(targetDir, fileName);
            if (targetFile.exists()) continue;

            try (InputStream is = resource.getInputStream()) {
                Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        return targetDir;
    }

    @Override
    public String analyze(String prompt) throws Exception {
        AbstractModel m = getOrLoadModel();

        PromptContext ctx;
        if (m.promptSupport().isPresent()) {
            ctx = m.promptSupport().get()
                    .builder()
                    .addSystemMessage("You are a performance monitoring assistant.")
                    .addUserMessage(prompt)
                    .build();
        } else {
            ctx = PromptContext.of(prompt);
        }

        var response = m.generate(UUID.randomUUID(), ctx, 0.2f, 400, (token, time) -> {});
        String rawOutput = response.responseText;

        String parsed = parseLineFormatToJson(rawOutput);
        // If parsing found nothing usable, fall back to the raw model output so it's visible for debugging
        // instead of silently rendering an empty result.
        return "[]".equals(parsed) ? rawOutput : parsed;
    }

    private String parseLineFormatToJson(String rawOutput) {
        StringBuilder json = new StringBuilder("[");
        String[] lines = rawOutput.split("\\r?\\n");
        boolean first = true;

        for (String line : lines) {
            if (!line.contains("|")) continue;
            String[] parts = line.split("\\|");
            if (parts.length < 3) continue;

            String endpoint = parts[0].trim().replace("\"", "");
            String causeRaw = parts[1].trim().toUpperCase();
            String cause = causeRaw.contains("DATABASE") ? "DATABASE" : causeRaw.contains("OTHER") ? "OTHER" : "CPU";
            String suggestion = parts[2].trim().replace("\"", "");

            if (!first) json.append(",");
            json.append("{")
                    .append("\"endpoint\":\"").append(escape(endpoint)).append("\",")
                    .append("\"issue\":\"Slow response time detected\",")
                    .append("\"cause\":\"").append(cause).append("\",")
                    .append("\"severity\":\"HIGH\",")
                    .append("\"suggestion\":\"").append(escape(suggestion)).append("\"")
                    .append("}");
            first = false;
        }
        json.append("]");
        return json.toString();
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}