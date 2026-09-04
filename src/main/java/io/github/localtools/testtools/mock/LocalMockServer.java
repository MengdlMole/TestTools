package io.github.localtools.testtools.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.localtools.testtools.http.HttpModels.MutableResponse;
import io.github.localtools.testtools.http.HttpModels.RequestSnapshot;
import io.github.localtools.testtools.runner.VariableResolver;
import io.github.localtools.testtools.security.ApiSecurityHandler;
import io.github.localtools.testtools.security.SecurityHandlerRegistry;
import io.github.localtools.testtools.security.SignContext;
import io.github.localtools.testtools.security.VerificationResult;
import io.github.localtools.testtools.workspace.WorkspaceModels.EnvironmentConfig;
import io.github.localtools.testtools.workspace.WorkspaceModels.MockDefinition;
import io.github.localtools.testtools.workspace.WorkspaceModels.WorkspaceConfig;
import io.github.localtools.testtools.workspace.WorkspaceService;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class LocalMockServer implements SmartLifecycle {
    private final WorkspaceService workspace;
    private final SecurityHandlerRegistry handlers;
    private final VariableResolver variables;
    private final ArrayDeque<MockCall> recentCalls = new ArrayDeque<>();
    private HttpServer server;
    private ExecutorService executor;
    private volatile boolean running;

    public LocalMockServer(WorkspaceService workspace, SecurityHandlerRegistry handlers, VariableResolver variables) {
        this.workspace = workspace;
        this.handlers = handlers;
        this.variables = variables;
    }

    @Override
    public void start() {
        if (running) return;
        try {
            int port = workspace.config().mockPort() == null ? 19090 : workspace.config().mockPort();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            executor = Executors.newVirtualThreadPerTaskExecutor();
            server.setExecutor(executor);
            server.createContext("/", this::handle);
            server.start();
            running = true;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start local mock server", e);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        RequestSnapshot request = new RequestSnapshot(exchange.getRequestMethod(), exchange.getRequestURI(),
                exchange.getRequestHeaders(), requestBody);
        MockDefinition mock = workspace.mocks().stream().filter(item -> matches(item, request)).findFirst().orElse(null);
        if (mock == null) {
            record(request, 404, "No mock matched");
            send(exchange, new MutableResponse(404, Map.of("Content-Type", "application/json"),
                    "{\"error\":\"No mock matched\"}".getBytes()));
            return;
        }

        SignContext context = signContext();
        ApiSecurityHandler handler = handlers.byId(text(mock.securityHandler(), "none"));
        VerificationResult verification = handler.verifyMockRequest(context, request);
        if (!verification.success()) {
            record(request, 401, verification.message());
            send(exchange, new MutableResponse(401, Map.of("Content-Type", "application/json"),
                    ("{\"error\":\"" + verification.message() + "\"}").getBytes()));
            return;
        }

        try {
            if (mock.response().delayMs() != null && mock.response().delayMs() > 0) {
                Thread.sleep(mock.response().delayMs());
            }
            byte[] body = responseBody(mock, request, context.variables());
            MutableResponse response = new MutableResponse(
                    mock.response().status() == null ? 200 : mock.response().status(),
                    mock.response().headers(), body);
            response.headers().putIfAbsent("Content-Type", "application/json");
            handler.signMockResponse(context, request, response);
            record(request, response.status(), mock.name());
            send(exchange, response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            send(exchange, new MutableResponse(500, Map.of(), new byte[0]));
        }
    }

    private boolean matches(MockDefinition mock, RequestSnapshot request) {
        if (Boolean.FALSE.equals(mock.enabled()) || mock.request() == null) return false;
        if (mock.request().method() != null && !mock.request().method().equalsIgnoreCase(request.method())) return false;
        if (mock.request().path() != null && !mock.request().path().equals(request.uri().getPath())) return false;
        if (mock.request().headers() != null) {
            for (var entry : mock.request().headers().entrySet()) {
                if (!entry.getValue().equals(request.firstHeader(entry.getKey()))) return false;
            }
        }
        if (mock.request().body() != null) {
            try {
                JsonNode actual = workspace.jsonMapper().readTree(request.body());
                if (!mock.request().body().equals(actual)) return false;
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }

    private byte[] responseBody(MockDefinition mock, RequestSnapshot request, Map<String, String> baseVariables) throws IOException {
        Map<String, String> values = new LinkedHashMap<>(baseVariables);
        values.put("request.body", request.bodyText());
        values.put("random.uuid", UUID.randomUUID().toString());
        if (mock.response().bodyFile() != null) {
            return variables.resolve(new String(workspace.bodyFile(mock.response().bodyFile())), values).getBytes();
        }
        JsonNode resolved = variables.resolve(mock.response().body(), values);
        return resolved == null ? new byte[0] : workspace.jsonMapper().writeValueAsBytes(resolved);
    }

    private SignContext signContext() {
        WorkspaceConfig config = workspace.config();
        EnvironmentConfig environment = workspace.environment(config.defaultEnvironment());
        Map<String, String> values = new LinkedHashMap<>();
        if (config.variables() != null) values.putAll(config.variables());
        if (environment.variables() != null) values.putAll(environment.variables());
        return new SignContext(values, workspace.secrets(environment.secretRef()));
    }

    private void send(HttpExchange exchange, MutableResponse response) throws IOException {
        response.headers().forEach((key, value) -> exchange.getResponseHeaders().set(key, value));
        exchange.sendResponseHeaders(response.status(), response.body().length);
        exchange.getResponseBody().write(response.body());
        exchange.close();
    }

    private synchronized void record(RequestSnapshot request, int status, String matched) {
        recentCalls.addFirst(new MockCall(Instant.now().toString(), request.method(), request.uri().toString(), status, matched));
        while (recentCalls.size() > 50) recentCalls.removeLast();
    }

    public synchronized List<MockCall> recentCalls() { return new ArrayList<>(recentCalls); }
    public int port() { return workspace.config().mockPort() == null ? 19090 : workspace.config().mockPort(); }
    private String text(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

    @Override public void stop() {
        if (server != null) server.stop(0);
        if (executor != null) executor.close();
        running = false;
    }
    @Override public boolean isRunning() { return running; }
    @Override public boolean isAutoStartup() { return true; }

    public record MockCall(String time, String method, String path, int responseStatus, String matchedMock) {}
}
