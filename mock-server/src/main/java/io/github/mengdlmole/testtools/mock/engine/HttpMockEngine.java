package io.github.mengdlmole.testtools.mock.engine;

import static io.github.mengdlmole.testtools.http.HttpHeaderSupport.putIfAbsentIgnoreCase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mengdlmole.testtools.http.transport.MutableResponse;
import io.github.mengdlmole.testtools.http.transport.RequestSnapshot;
import io.github.mengdlmole.testtools.mock.callback.CallbackTask;
import io.github.mengdlmole.testtools.mock.config.MockCatalog;
import io.github.mengdlmole.testtools.mock.config.MockCatalog.LoadedMock;
import io.github.mengdlmole.testtools.mock.config.MockDefinitionRepository;
import io.github.mengdlmole.testtools.mock.model.MockDefinition;
import io.github.mengdlmole.testtools.security.HttpSecurityHandler;
import io.github.mengdlmole.testtools.security.SecurityHandlerRegistry;
import io.github.mengdlmole.testtools.security.SignContext;
import io.github.mengdlmole.testtools.security.VerificationResult;
import io.github.mengdlmole.testtools.workspace.VariableResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Matches a framework-neutral request against one immutable Mock catalog.
 */
public final class HttpMockEngine {
  private final MockDefinitionRepository definitions;
  private final SecurityHandlerRegistry handlers;
  private final VariableResolver variables;
  private final ObjectMapper jsonMapper;

  public HttpMockEngine(
      MockDefinitionRepository definitions,
      SecurityHandlerRegistry handlers,
      VariableResolver variables,
      ObjectMapper jsonMapper) {
    this.definitions = definitions;
    this.handlers = handlers;
    this.variables = variables;
    this.jsonMapper = jsonMapper;
  }

  public MockExchange execute(MockRequest incoming) {
    RequestSnapshot request = incoming.snapshot();
    MockCatalog catalog = definitions.current();
    LoadedMock loaded =
        catalog.definitions().stream()
            .filter(item -> !Boolean.FALSE.equals(item.definition().enabled()))
            .filter(item -> matches(item.definition(), incoming))
            .findFirst()
            .orElse(null);
    if (loaded == null) {
      return result(404, "No mock matched", jsonError("No mock matched"));
    }

    MockDefinition mock = loaded.definition();
    SignContext context = catalog.signContext();
    HttpSecurityHandler handler = handlers.byId(text(mock.securityHandler(), "none"));
    VerificationResult verification = handler.verifyMockRequest(context, request);
    if (!verification.success()) {
      return result(401, mock.name(), jsonError(verification.message()));
    }

    byte[] body = responseBody(loaded, request, context.variables());
    MutableResponse response = new MutableResponse(status(mock), mock.response().headers(), body);
    if (mock.response().body() != null) {
      putIfAbsentIgnoreCase(response.headers(), "Content-Type", "application/json");
    }
    handler.signMockResponse(context, request, response);

    List<CallbackTask> callbacks =
        loaded.callbacks().stream()
            .map(
                callback ->
                    new CallbackTask(
                        mock.name(), callback.definition(), callback.bodyFile(), request, context))
            .toList();
    long delayMs = mock.response().delayMs() == null ? 0 : Math.max(0, mock.response().delayMs());
    return new MockExchange(response, callbacks, mock.name(), delayMs);
  }

  private MockExchange result(int status, String matched, byte[] body) {
    return new MockExchange(
        new MutableResponse(status, Map.of("Content-Type", "application/json"), body),
        List.of(),
        matched,
        0);
  }

  private boolean matches(MockDefinition mock, MockRequest request) {
    RequestSnapshot snapshot = request.snapshot();
    if (mock.request().method() != null
        && !mock.request().method().equalsIgnoreCase(snapshot.method())) {
      return false;
    }
    if (!mock.request().path().equals(snapshot.uri().getPath())) {
      return false;
    }
    if (mock.request().query() != null) {
      for (var entry : mock.request().query().entrySet()) {
        if (!entry.getValue().equals(request.firstQuery(entry.getKey()))) {
          return false;
        }
      }
    }
    if (mock.request().headers() != null) {
      for (var entry : mock.request().headers().entrySet()) {
        if (!entry.getValue().equals(snapshot.firstHeader(entry.getKey()))) {
          return false;
        }
      }
    }
    if (mock.request().body() != null) {
      try {
        JsonNode actual = jsonMapper.readTree(snapshot.body());
        if (!mock.request().body().equals(actual)) {
          return false;
        }
      } catch (IOException error) {
        return false;
      }
    }
    return true;
  }

  private byte[] responseBody(
      LoadedMock loaded, RequestSnapshot request, Map<String, String> baseVariables) {
    MockDefinition mock = loaded.definition();
    try {
      Map<String, String> values = MockRequestVariables.create(request, baseVariables);
      if (mock.response().bodyFile() != null) {
        return variables
            .resolve(new String(loaded.responseBodyFile(), StandardCharsets.UTF_8), values)
            .getBytes(StandardCharsets.UTF_8);
      }
      JsonNode resolved = variables.resolve(mock.response().body(), values);
      return resolved == null ? new byte[0] : jsonMapper.writeValueAsBytes(resolved);
    } catch (IOException error) {
      throw new IllegalArgumentException("Cannot create response for mock " + mock.name(), error);
    }
  }

  private int status(MockDefinition mock) {
    return mock.response().status() == null ? 200 : mock.response().status();
  }

  private byte[] jsonError(String message) {
    try {
      return jsonMapper.writeValueAsBytes(
          Map.of("error", message == null ? "unknown error" : message));
    } catch (IOException impossible) {
      return "{\"error\":\"unknown error\"}".getBytes(StandardCharsets.UTF_8);
    }
  }

  private String text(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
