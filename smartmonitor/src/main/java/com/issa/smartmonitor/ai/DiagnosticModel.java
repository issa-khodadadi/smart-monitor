package com.issa.smartmonitor.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Softmax regression trained for this dashboard. Weights live in the model resource, not in source.
 */
public final class DiagnosticModel {

    private final String id;
    private final String version;
    private final double holdoutAccuracy;
    private final double minConfidence;
    private final double tailAvgFloor;
    private final String[] featureNames;
    private final String[] labels;
    private final double[] mean;
    private final double[] std;
    private final double[][] weights;
    private final double[] bias;
    private final JsonNode reports;
    private final JsonNode evidence;
    private final String footer;
    private final JsonNode fixtures;

    private DiagnosticModel(JsonNode root) {
        this.id = root.path("id").asText();
        this.version = root.path("version").asText();
        this.holdoutAccuracy = root.path("holdoutAccuracy").asDouble();
        this.minConfidence = root.path("minConfidence").asDouble();
        this.tailAvgFloor = root.path("tailAvgFloor").asDouble();
        this.featureNames = textArray(root.path("featureNames"));
        this.labels = textArray(root.path("labels"));
        this.mean = doubleArray(root.path("mean"));
        this.std = doubleArray(root.path("std"));
        this.bias = doubleArray(root.path("bias"));
        this.weights = matrix(root.path("weights"));
        this.reports = root.path("reports");
        this.evidence = root.path("evidence");
        this.footer = root.path("footer").asText();
        this.fixtures = root.path("fixtures");
        if (featureNames.length == 0 || labels.length == 0 || weights.length != labels.length) {
            throw new AiAnalysisException("Diagnostic model resource is incomplete.", null);
        }
    }

    public static DiagnosticModel load(InputStream in, ObjectMapper mapper) {
        try {
            return new DiagnosticModel(mapper.readTree(in));
        } catch (AiAnalysisException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiAnalysisException("Unable to read the diagnostic model.", ex);
        }
    }

    public Prediction predict(Map<String, Double> raw) {
        double[] features = new double[featureNames.length];
        for (int i = 0; i < featureNames.length; i++) {
            features[i] = (transform(featureNames[i], raw) - mean[i]) / std[i];
        }
        double[] logits = new double[labels.length];
        double peak = Double.NEGATIVE_INFINITY;
        for (int c = 0; c < labels.length; c++) {
            double sum = bias[c];
            double[] row = weights[c];
            for (int j = 0; j < features.length; j++) {
                sum += row[j] * features[j];
            }
            logits[c] = sum;
            if (sum > peak) {
                peak = sum;
            }
        }
        double normalizer = 0;
        double[] probabilities = new double[labels.length];
        for (int c = 0; c < labels.length; c++) {
            probabilities[c] = Math.exp(logits[c] - peak);
            normalizer += probabilities[c];
        }
        int best = 0;
        for (int c = 0; c < probabilities.length; c++) {
            probabilities[c] /= normalizer;
            if (probabilities[c] > probabilities[best]) {
                best = c;
            }
        }
        return new Prediction(labels[best], probabilities[best]);
    }

    public Map<String, Object> finding(String label, Map<String, String> slots) {
        JsonNode report = reports.path(label);
        Map<String, Object> finding = new LinkedHashMap<>();
        finding.put("label", label);
        finding.put("problem", fill(report.path("problem").asText(), slots));
        finding.put("severity", report.path("severity").asText());
        finding.put("summary", fill(report.path("summary").asText(), slots));
        finding.put("suggestion", fill(report.path("suggestion").asText(), slots));
        finding.put("weight", report.path("weight").asDouble());
        List<String> lines = new ArrayList<>();
        for (JsonNode key : report.path("evidence")) {
            String template = evidence.path(key.asText()).asText();
            if (!template.isBlank()) {
                lines.add(fill(template, slots));
            }
        }
        finding.put("evidence", lines);
        return finding;
    }

    public String locate(String label) {
        return reports.path(label).path("locate").asText();
    }

    public double weight(String label) {
        return reports.path(label).path("weight").asDouble();
    }

    public String footer(Map<String, String> slots) {
        return fill(footer, slots);
    }

    public String id() {
        return id;
    }

    public String version() {
        return version;
    }

    public double holdoutAccuracy() {
        return holdoutAccuracy;
    }

    public double minConfidence() {
        return minConfidence;
    }

    public JsonNode fixtures() {
        return fixtures;
    }

    private double transform(String feature, Map<String, Double> raw) {
        return switch (feature) {
            case "log1p_calls" -> Math.log1p(value(raw, "calls"));
            case "log1p_avg_ms" -> Math.log1p(value(raw, "avg_ms"));
            case "log1p_p95_ms" -> Math.log1p(value(raw, "p95_ms"));
            case "log1p_p99_ms" -> Math.log1p(value(raw, "p99_ms"));
            case "log1p_max_ms" -> Math.log1p(value(raw, "max_ms"));
            case "db_ratio" -> value(raw, "db_ratio");
            case "error_rate" -> value(raw, "error_rate");
            case "log1p_calls_per_request" -> Math.log1p(value(raw, "calls_per_request"));
            case "self_ratio" -> value(raw, "self_ratio");
            case "log1p_memory_kb" -> Math.log1p(value(raw, "memory_kb"));
            case "tail_ratio" -> value(raw, "p99_ms") / Math.max(value(raw, "avg_ms"), tailAvgFloor);
            case "fragmented" -> value(raw, "fragmented");
            case "repository_share" -> value(raw, "repository_share");
            case "log1p_errors" -> Math.log1p(value(raw, "errors"));
            case "db_x_log_avg" -> value(raw, "db_ratio") * Math.log1p(value(raw, "avg_ms"));
            case "cpr_x_db" -> Math.log1p(value(raw, "calls_per_request")) * value(raw, "db_ratio");
            case "error_x_log_calls" -> value(raw, "error_rate") * Math.log1p(value(raw, "calls"));
            default -> throw new AiAnalysisException("Unknown diagnostic feature: " + feature, null);
        };
    }

    private static double value(Map<String, Double> raw, String key) {
        Double value = raw.get(key);
        return value == null ? 0 : value;
    }

    static String fill(String template, Map<String, String> slots) {
        String filled = template;
        for (Map.Entry<String, String> slot : slots.entrySet()) {
            filled = filled.replace("{" + slot.getKey() + "}", slot.getValue());
        }
        return filled;
    }

    private static String[] textArray(JsonNode node) {
        String[] values = new String[node.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = node.get(i).asText();
        }
        return values;
    }

    private static double[] doubleArray(JsonNode node) {
        double[] values = new double[node.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = node.get(i).asDouble();
        }
        return values;
    }

    private static double[][] matrix(JsonNode node) {
        double[][] values = new double[node.size()][];
        for (int i = 0; i < values.length; i++) {
            values[i] = doubleArray(node.get(i));
        }
        return values;
    }

    public record Prediction(String label, double confidence) {
    }
}
