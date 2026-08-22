package com.issa.smartmonitor.ai;

import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.ModelSupport;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.safetensors.SafeTensorSupport;
import com.github.tjake.jlama.safetensors.prompt.PromptContext;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public class JlamaProvider implements AiAnalysisProvider {

    private static final String BUNDLED_MODEL_RESOURCE_PATH = "models/tinyllama";
    private static final String[] MODEL_FILES = {"config.json", "tokenizer.json", "model.safetensors"};

    private final String modelName;
    private final String workingDirectory;
    private volatile AbstractModel model;

    public JlamaProvider(String modelName, String workingDirectory) {
        this.modelName = modelName;
        this.workingDirectory = workingDirectory;
    }

    private synchronized AbstractModel getOrLoadModel() {
        if (model == null) {
            try {
                File extractedPath = extractBundledModelIfPresent();
                File localModelPath = extractedPath != null
                        ? extractedPath
                        : SafeTensorSupport.maybeDownloadModel(workingDirectory, modelName);

                model = ModelSupport.loadModel(localModelPath, DType.F32, DType.I8);
            } catch (Exception e) {
                throw new AiAnalysisException("Failed to load Jlama model: " + e.getMessage(), e);
            }
        }
        return model;
    }

    private File extractBundledModelIfPresent() throws Exception {
        ClassPathResource marker = new ClassPathResource(BUNDLED_MODEL_RESOURCE_PATH + "/config.json");
        if (!marker.exists()) {
            return null;
        }

        File targetDir = new File(workingDirectory, "bundled-tinyllama");
        targetDir.mkdirs();

        for (String fileName : MODEL_FILES) {
            File targetFile = new File(targetDir, fileName);
            if (targetFile.exists()) continue;

            try (InputStream is = new ClassPathResource(BUNDLED_MODEL_RESOURCE_PATH + "/" + fileName).getInputStream()) {
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
                    .addSystemMessage("You are a backend performance analyst. Respond only in the requested JSON format.")
                    .addUserMessage(prompt)
                    .build();
        } else {
            ctx = PromptContext.of(prompt);
        }

        var response = m.generate(UUID.randomUUID(), ctx, 0.2f, 1500, (token, time) -> {});
        return response.responseText;
    }
}