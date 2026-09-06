package io.github.localtools.testtools.mock;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.localtools.testtools.http.HttpModels.MutableResponse;
import io.github.localtools.testtools.http.HttpModels.RequestSnapshot;
import io.github.localtools.testtools.runner.VariableResolver;
import io.github.localtools.testtools.security.HttpSecurityHandler;
import io.github.localtools.testtools.security.SecurityHandlerRegistry;
import io.github.localtools.testtools.security.SignContext;
import io.github.localtools.testtools.security.VerificationResult;
import io.github.localtools.testtools.workspace.WorkspaceModels.EnvironmentConfig;
import io.github.localtools.testtools.workspace.WorkspaceModels.MockDefinition;
import io.github.localtools.testtools.workspace.WorkspaceModels.WorkspaceConfig;
import io.github.localtools.testtools.workspace.WorkspaceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.localtools.testtools.mock.MockRuntimeModels.CallbackTask;
import static io.github.localtools.testtools.mock.MockRuntimeModels.MockExchange;

@Component
class HttpMockEngine {
    private final WorkspaceService workspace;
    private final SecurityHandlerRegistry handlers;
    private final VariableResolver variables;
    private final MockCallStore calls;

    HttpMockEngine(WorkspaceService workspace, SecurityHandlerRegistry handlers,
                   VariableResolver variables, MockCallStore calls) {
        this.workspace = workspace;
        this.handlers = handlers;
        this.variables = variables;
        this.calls = calls;
    }

    MockExchange execute(HttpServletRequest servletRequest, byte[] requestBody) {
        RequestSnapshot request = snapshot(servletRequest, requestBody);
        MockDefinition mock = workspace.mocks().stream()
                .filter(this::isHttp)
                .filter(item -> matches(item, servletRequest, request))
                .findFirst().orElse(null);
        if (mock == null) return result(request, 404, "No mock matched",
                jsonError("No mock matched"), List.of());

        SignContext context = signContext();
        HttpSecurityHandler handler = handlers.byId(text(mock.securityHandler(), "none"));
        VerificationResult verification = handler.verifyMockRequest(context, request);
        if (!verification.success()) return result(request, 401, mock.name(),
                jsonError(verification.message()), List.of());

        delay(mock.response().delayMs());
        byte[] body = responseBody(mock, request, context.variables());
        MutableResponse response = new MutableResponse(status(mock), mock.response().headers(), body);
        response.headers().putIfAbsent("Content-Type", "application/json");
        handler.signMockResponse(context, request, response);

        List<CallbackTask> callbacks = mock.afterResponse() == null ? List.of()
                : mock.afterResponse().stream()
                .map(definition -> new CallbackTask(mock.name(), definition, request, context)).toList();
        calls.record(request.method(), request.uri().toString(), response.status(), mock.name());
        return new MockExchange(response, callbacks);
    }

    private MockExchange result(RequestSnapshot request, int status, String matched,
                                byte[] body, List<CallbackTask> callbacks) {
        calls.record(request.method(), request.uri().toString(), status, matched);
        return new MockExchange(new MutableResponse(status, Map.of("Content-Type", "application/json"), body), callbacks);
    }

    private boolean isHttp(MockDefinition mock) {
        String protocol = text(mock.protocol(), "http");
        if (!"http".equalsIgnoreCase(protocol)) {
            throw new IllegalArgumentException("Unsupported mock protocol '" + protocol + "'; only http is implemented");
        }
        return !Boolean.FALSE.equals(mock.enabled());
    }

    private boolean matches(MockDefinition mock, HttpServletRequest servletRequest, RequestSnapshot request) {
        if (mock.request() == null) return false;
        if (mock.request().method() != null && !mock.request().method().equalsIgnoreCase(request.method())) return false;
        if (mock.request().path() != null && !mock.request().path().equals(request.uri().getPath())) return false;
        if (mock.request().query() != null) {
            for (var entry : mock.request().query().entrySet()) {
                if (!entry.getValue().equals(servletRequest.getParameter(entry.getKey()))) return false;
            }
        }
        if (mock.request().headers() != null) {
            for (var entry : mock.request().headers().entrySet()) {
                if (!entry.getValue().equals(request.firstHeader(entry.getKey()))) return false;
            }
        }
        if (mock.request().body() != null) {
            try {
                JsonNode actual = workspace.jsonMapper().readTree(request.body());
                if (!mock.request().body().equals(actual)) return false;
            } catch (IOException error) {
                return false;
            }
        }
        return true;
    }

    private RequestSnapshot snapshot(HttpServletRequest request, byte[] body) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names != null) while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, Collections.list(request.getHeaders(name)));
        }
        String uri = request.getRequestURI();
        if (request.getQueryString() != null) uri += "?" + request.getQueryString();
        return new RequestSnapshot(request.getMethod(), URI.create(uri), headers,
                body == null ? new byte[0] : body);
    }

    private byte[] responseBody(MockDefinition mock, RequestSnapshot request,
                                Map<String, String> baseVariables) {
        try {
            Map<String, String> values = requestVariables(request, baseVariables);
            if (mock.response().bodyFile() != null) {
                return variables.resolve(new String(workspace.bodyFile(mock.response().bodyFile()), StandardCharsets.UTF_8), values)
                        .getBytes(StandardCharsets.UTF_8);
            }
            JsonNode resolved = variables.resolve(mock.response().body(), values);
            return resolved == null ? new byte[0] : workspace.jsonMapper().writeValueAsBytes(resolved);
        } catch (IOException error) {
            throw new IllegalArgumentException("Cannot create response for mock " + mock.name(), error);
        }
    }

    static Map<String, String> requestVariables(RequestSnapshot request, Map<String, String> baseVariables) {
        Map<String, String> values = new LinkedHashMap<>(baseVariables);
        values.put("request.body", request.bodyText());
        values.put("request.method", request.method());
        values.put("request.path", request.uri().getPath());
        values.put("random.uuid", UUID.randomUUID().toString());
        return values;
    }

    private SignContext signContext() {
        WorkspaceConfig config = workspace.config();
        EnvironmentConfig environment = workspace.environment(config.defaultEnvironment());
        Map<String, String> values = new LinkedHashMap<>();
        if (config.variables() != null) values.putAll(config.variables());
        if (environment.variables() != null) values.putAll(environment.variables());
        return new SignContext(values, workspace.secrets(environment.secretRef()));
    }

    private void delay(Long delayMs) {
        if (delayMs == null || delayMs <= 0) return;
        try { Thread.sleep(delayMs); }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Mock response delay interrupted", error);
        }
    }

    private int status(MockDefinition mock) { return mock.response().status() == null ? 200 : mock.response().status(); }
    private byte[] jsonError(String message) {
        try { return workspace.jsonMapper().writeValueAsBytes(Map.of("error", message == null ? "unknown error" : message)); }
        catch (IOException impossible) { return "{\"error\":\"unknown error\"}".getBytes(StandardCharsets.UTF_8); }
    }
    private String text(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
