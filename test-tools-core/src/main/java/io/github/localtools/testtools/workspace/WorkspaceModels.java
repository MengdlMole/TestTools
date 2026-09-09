package io.github.localtools.testtools.workspace;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public final class WorkspaceModels {
    private WorkspaceModels() {}

    public record WorkspaceConfig(String defaultEnvironment, Integer mockPort,
                                  Map<String, String> variables, List<SecurityRule> securityRules) {}
    public record SecurityRule(String operationId, String method, String pathPattern, String handler) {}
    public record EnvironmentConfig(String name, String baseUrl, String secretRef,
                                    String defaultSecurityHandler, Map<String, String> variables) {}
    public record SecretFile(Map<String, Map<String, String>> secrets) {}
    public record TestCase(String name, String environment, Map<String, String> variables,
                           List<TestStep> steps) {}
    public record TestSuite(String name, List<String> cases, Boolean stopOnFailure, Integer repeat) {}
    public record TestStep(String name, String operationId, String protocol, String method, String path,
                           Map<String, String> query, Map<String, String> headers,
                           String bodyFile, String globalBodyFile, String caseBodyFile,
                           JsonNode body, String securityHandler,
                           Map<String, String> extract, List<AssertionDefinition> assertions) {}
    public record AssertionDefinition(String type, String path, String operator, JsonNode expected) {}
    public record MockDefinition(String name, String protocol, Boolean enabled, Integer priority,
                                 MockRequest request, MockResponse response, String securityHandler,
                                 List<AfterResponseDefinition> afterResponse) {}
    public record MockRequest(String method, String path, Map<String, String> query,
                              Map<String, String> headers, JsonNode body) {}
    public record MockResponse(Integer status, Map<String, String> headers, JsonNode body,
                               String bodyFile, Long delayMs) {}
    public record AfterResponseDefinition(String name, String protocol, Long delayMs, Long timeoutMs,
                                          String securityHandler, RetryDefinition retry,
                                          CallbackRequest request) {}
    public record RetryDefinition(Integer maxAttempts, Long intervalMs) {}
    public record CallbackRequest(String method, String url, Map<String, String> headers,
                                  JsonNode body, String bodyFile) {}
}
