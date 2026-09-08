package io.github.localtools.testtools.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.JsonPath;
import io.github.localtools.testtools.http.HttpExecutor;
import io.github.localtools.testtools.http.HttpModels.MutableRequest;
import io.github.localtools.testtools.http.SensitiveDataMasker;
import io.github.localtools.testtools.runner.RunModels.AssertionResult;
import io.github.localtools.testtools.runner.RunModels.CaseResult;
import io.github.localtools.testtools.runner.RunModels.ErrorDetail;
import io.github.localtools.testtools.runner.RunModels.StepResult;
import io.github.localtools.testtools.security.HttpSecurityHandler;
import io.github.localtools.testtools.security.SecurityHandlerRegistry;
import io.github.localtools.testtools.security.SignContext;
import io.github.localtools.testtools.security.VerificationResult;
import io.github.localtools.testtools.workspace.WorkspaceModels.*;
import io.github.localtools.testtools.workspace.WorkspaceService;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestCaseRunner {
    private final WorkspaceService workspace;
    private final VariableResolver variables;
    private final SecurityHandlerRegistry securityHandlers;
    private final HttpExecutor http;
    private final AssertionEngine assertions;

    public TestCaseRunner(WorkspaceService workspace, VariableResolver variables,
                          SecurityHandlerRegistry securityHandlers, HttpExecutor http,
                          AssertionEngine assertions) {
        this.workspace = workspace;
        this.variables = variables;
        this.securityHandlers = securityHandlers;
        this.http = http;
        this.assertions = assertions;
    }

    public CaseResult run(String caseName) {
        long started = System.nanoTime();
        WorkspaceConfig config = workspace.config();
        TestCase testCase = workspace.testCase(caseName);
        String environmentName = text(testCase.environment(), config.defaultEnvironment());
        EnvironmentConfig environment = workspace.environment(environmentName);
        Map<String, String> values = new LinkedHashMap<>();
        putAll(values, config.variables());
        putAll(values, environment.variables());
        putAll(values, testCase.variables());
        Map<String, String> secrets = workspace.secrets(environment.secretRef());
        SignContext signContext = new SignContext(values, secrets);
        List<StepResult> stepResults = new ArrayList<>();
        boolean success = true;

        for (TestStep step : testCase.steps()) {
            MutableRequest request = null;
            String handlerId = step.securityHandler();
            try {
                requireHttp(step.protocol(), "case step " + step.name());
                HttpSecurityHandler handler = securityHandlers.resolve(step, config, environment.defaultSecurityHandler());
                handlerId = handler.id();
                request = createRequest(environment, step, values);
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

    private MutableRequest createRequest(EnvironmentConfig environment, TestStep step, Map<String, String> values) {
        String path = variables.resolve(step.path(), values);
        StringBuilder url = new StringBuilder(environment.baseUrl()).append(path);
        if (step.query() != null && !step.query().isEmpty()) {
            url.append('?');
            step.query().forEach((key, value) -> url.append(encode(key)).append('=')
                    .append(encode(variables.resolve(value, values))).append('&'));
            url.setLength(url.length() - 1);
        }
        Map<String, String> headers = new LinkedHashMap<>();
        if (step.headers() != null) step.headers().forEach((key, value) -> headers.put(key, variables.resolve(value, values)));
        byte[] body = body(step, values);
        if (body.length > 0) headers.putIfAbsent("Content-Type", "application/json");
        return new MutableRequest(text(step.method(), "GET").toUpperCase(), URI.create(url.toString()), headers, body);
    }

    private byte[] body(TestStep step, Map<String, String> values) {
        try {
            if (step.bodyFile() != null) {
                return variables.resolve(new String(workspace.bodyFile(step.bodyFile()), StandardCharsets.UTF_8), values)
                        .getBytes(StandardCharsets.UTF_8);
            }
            JsonNode body = variables.resolve(step.body(), values);
            return body == null ? new byte[0] : workspace.jsonMapper().writeValueAsBytes(body);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot build body for step " + step.name(), e);
        }
    }

    private void extract(TestStep step, String body, Map<String, String> values) {
        if (step.extract() == null) return;
        step.extract().forEach((name, path) -> {
            Object extracted = JsonPath.read(body, path);
            values.put(name, String.valueOf(extracted));
        });
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private void requireHttp(String protocol, String source) {
        if (protocol != null && !protocol.isBlank() && !"http".equalsIgnoreCase(protocol)) {
            throw new IllegalArgumentException("Unsupported protocol '" + protocol + "' in " + source + "; only http is implemented");
        }
    }
    private String text(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private void putAll(Map<String, String> target, Map<String, String> source) { if (source != null) target.putAll(source); }

    private Map<String, String> mask(Map<String, String> headers) {
        return SensitiveDataMasker.maskHeaders(headers);
    }
    private Map<String, String> flatten(Map<String, List<String>> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        headers.forEach((key, value) -> result.put(key, String.join(", ", value)));
        return mask(result);
    }
}
