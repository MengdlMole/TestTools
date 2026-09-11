package io.github.mengdlmole.testtools.yamlrunner.result;

import java.util.List;
import java.util.Map;

public record StepResult(String name, boolean success, String securityHandler,
                         String method, String url, Map<String, String> requestHeaders,
                         String requestBody, int responseStatus, Map<String, String> responseHeaders,
                         String responseBody, long durationMs, String verification,
                         List<AssertionResult> assertions, ErrorDetail error) {}
