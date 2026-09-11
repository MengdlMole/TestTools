package io.github.localtools.testtools.yamlrunner.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.JsonPath;
import io.github.localtools.testtools.http.HttpExecutor;
import io.github.localtools.testtools.http.HttpHeaderSupport;
import io.github.localtools.testtools.http.HttpRequestUriBuilder;
import io.github.localtools.testtools.http.MutableRequest;
import io.github.localtools.testtools.http.SensitiveDataMasker;
import io.github.localtools.testtools.workspace.VariableResolver;
import io.github.localtools.testtools.yamlrunner.YamlSecurityResolver;
import io.github.localtools.testtools.yamlrunner.YamlWorkspace;
import io.github.localtools.testtools.yamlrunner.result.AssertionResult;
import io.github.localtools.testtools.yamlrunner.result.CaseResult;
import io.github.localtools.testtools.yamlrunner.result.ErrorDetail;
import io.github.localtools.testtools.yamlrunner.result.StepResult;
import io.github.localtools.testtools.security.HttpSecurityHandler;
import io.github.localtools.testtools.security.SignContext;
import io.github.localtools.testtools.security.VerificationResult;
import io.github.localtools.testtools.workspace.EnvironmentConfig;
import io.github.localtools.testtools.workspace.EnvironmentContext;
import io.github.localtools.testtools.workspace.TestWorkspace;
import io.github.localtools.testtools.workspace.WorkspaceConfig;
import io.github.localtools.testtools.yamlrunner.model.YamlCaseDefinition;
import io.github.localtools.testtools.yamlrunner.model.YamlStepDefinition;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class YamlCaseRunner {
    private final YamlWorkspace yamlWorkspace;
    private final TestWorkspace workspace;
    private final VariableResolver variables;
    private final YamlSecurityResolver security;
    private final HttpExecutor http;
    private final YamlAssertionEngine assertions;

    public YamlCaseRunner(YamlWorkspace yamlWorkspace, VariableResolver variables,
                          YamlSecurityResolver security, HttpExecutor http,
                          YamlAssertionEngine assertions) {
        this.yamlWorkspace = yamlWorkspace;
        this.workspace = yamlWorkspace.files();
        this.variables = variables;
        this.security = security;
        this.http = http;
        this.assertions = assertions;
    }

    public CaseResult run(String caseName) {
        long started = System.nanoTime();
        WorkspaceConfig config = workspace.config();
        YamlCaseDefinition testCase = yamlWorkspace.testCase(caseName);
        String environmentName = text(testCase.environment(), config.defaultEnvironment());
        EnvironmentContext environmentContext = workspace.environmentContext(environmentName, testCase.variables());
        EnvironmentConfig environment = environmentContext.environment();
        Map<String, String> values = new LinkedHashMap<>(environmentContext.variables());
        Map<String, String> secrets = environmentContext.secrets();
        SignContext signContext = new SignContext(values, secrets);
        List<StepResult> stepResults = new ArrayList<>();
        boolean success = true;

        for (YamlStepDefinition step : testCase.steps()) {
            MutableRequest request = null;
            String handlerId = step.securityHandler();
            try {
                requireHttp(step.protocol(), "case step " + step.name());
                HttpSecurityHandler handler = security.resolve(step, environment.defaultSecurityHandler());
                handlerId = handler.id();
                request = createRequest(caseName, environment, step, values);
                handler.signRequest(signContext, request);
                HttpExecutor.Exchange exchange = http.execute(request);
                VerificationResult verification = handler.verifyResponse(signContext, exchange.request(), exchange.response());
                List<AssertionResult> assertionResults = step.assertions() == null ? List.of()
                        : step.assertions().stream().map(item -> assertions.evaluate(item, exchange.response())).toList();
                boolean stepSuccess = verification.success() && assertionResults.stream().allMatch(AssertionResult::success);
                if (stepSuccess) extract(step, exchange.response().bodyText(), values);
                signContext = new SignContext(values, secrets);
                stepResults.add(new StepResult(step.name(), stepSuccess, handler.id(), request.method(),
                        SensitiveDataMasker.maskUri(request.uri()).toString(), mask(request.headers()), request.bodyText(),
                        exchange.response().status(), flatten(exchange.response().headers()), exchange.response().bodyText(),
                        exchange.response().durationMs(), verification.message(), assertionResults, null));
                success &= stepSuccess;
                if (!stepSuccess) break;
            } catch (Exception error) {
                stepResults.add(new StepResult(step.name(), false, handlerId,
                        request == null ? step.method() : request.method(),
                        request == null ? step.path() : SensitiveDataMasker.maskUri(request.uri()).toString(),
                        request == null ? Map.of() : mask(request.headers()),
                        request == null ? "" : request.bodyText(), 0, Map.of(), "", 0,
                        "not verified", List.of(), ErrorDetail.from(error)));
                success = false;
                break;
            }
        }
        return new CaseResult(testCase.name(), environmentName, success,
                Duration.ofNanos(System.nanoTime() - started).toMillis(), Map.copyOf(values), stepResults, null);
    }

    private MutableRequest createRequest(String caseName, EnvironmentConfig environment,
                                         YamlStepDefinition step, Map<String, String> values) {
        String path = variables.resolve(step.path(), values);
        List<HttpRequestUriBuilder.QueryParameter> query = step.query() == null ? List.of()
                : step.query().entrySet().stream()
                .map(entry -> new HttpRequestUriBuilder.QueryParameter(entry.getKey(),
                        variables.resolve(entry.getValue(), values)))
                .toList();
        Map<String, String> headers = new LinkedHashMap<>();
        if (step.headers() != null) step.headers().forEach((key, value) -> headers.put(key, variables.resolve(value, values)));
        BodyContent body = body(caseName, step, values);
        if (body.json() && body.bytes().length > 0) {
            HttpHeaderSupport.putIfAbsentIgnoreCase(headers, "Content-Type", "application/json");
        }
        return new MutableRequest(text(step.method(), "GET").toUpperCase(),
                HttpRequestUriBuilder.build(environment.baseUrl(), path, query), headers, body.bytes());
    }

    private BodyContent body(String caseName, YamlStepDefinition step, Map<String, String> values) {
        try {
            if (hasText(step.globalBodyFile())) {
                return new BodyContent(resolvedJson(workspace.globalJsonFile(step.globalBodyFile()), values), true);
            }
            if (hasText(step.caseBodyFile())) {
                return new BodyContent(resolvedJson(workspace.caseJsonFile(caseName, step.caseBodyFile()), values), true);
            }
            if (hasText(step.bodyFile())) {
                return new BodyContent(variables.resolve(
                        new String(workspace.fileBytes(step.bodyFile()), StandardCharsets.UTF_8), values)
                        .getBytes(StandardCharsets.UTF_8), false);
            }
            JsonNode body = variables.resolve(step.body(), values);
            return new BodyContent(body == null ? new byte[0] : workspace.jsonMapper().writeValueAsBytes(body), body != null);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot build body for step " + step.name(), e);
        }
    }

    private byte[] resolvedJson(java.nio.file.Path path, Map<String, String> values) throws Exception {
        JsonNode resolved = variables.resolve(workspace.readJson(path), values);
        return workspace.jsonMapper().writeValueAsBytes(resolved);
    }

    private void extract(YamlStepDefinition step, String body, Map<String, String> values) {
        if (step.extract() == null) return;
        step.extract().forEach((name, path) -> {
            Object extracted = JsonPath.read(body, path);
            values.put(name, String.valueOf(extracted));
        });
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private void requireHttp(String protocol, String source) {
        if (protocol != null && !protocol.isBlank() && !"http".equalsIgnoreCase(protocol)) {
            throw new IllegalArgumentException("Unsupported protocol '" + protocol + "' in " + source + "; only http is implemented");
        }
    }
    private String text(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private record BodyContent(byte[] bytes, boolean json) {}

    private Map<String, String> mask(Map<String, String> headers) {
        return SensitiveDataMasker.maskHeaders(headers);
    }
    private Map<String, String> flatten(Map<String, List<String>> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        headers.forEach((key, value) -> result.put(key, String.join(", ", value)));
        return mask(result);
    }
}
