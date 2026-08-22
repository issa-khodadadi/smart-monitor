package com.issa.smartmonitor.ai;

public interface AiAnalysisProvider {
    String analyze(String prompt) throws Exception;
}