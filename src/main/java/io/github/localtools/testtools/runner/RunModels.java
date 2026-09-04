package io.github.localtools.testtools.runner;

import java.util.List;
import java.util.Map;

public final class RunModels {
    private RunModels() {}

    public record AssertionResult(String type, boolean success, String message) {}

    public record StepResult(String name, boolean success, String securityHandler,
                             String method, String url, Map<String, String> requestHeaders,
                             String requestBody, int responseStatus, Map<String, String> responseHeaders,
                             String responseBody, long durationMs, String verification,
                             List<AssertionResult> assertions) {}

    public record CaseResult(String name, String environment, boolean success,
                             long durationMs, Map<String, String> variables, List<StepResult> steps) {}

    public record ExecutionRecord<T>(String runId, String resultFile, T result) {}

    public record SuiteResult(String name, boolean success, int executedCases,
                              int totalCases, long durationMs,
                              List<ExecutionRecord<CaseResult>> cases) {}
}
