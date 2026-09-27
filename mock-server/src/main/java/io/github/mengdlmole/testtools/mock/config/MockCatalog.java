package io.github.mengdlmole.testtools.mock.config;

import io.github.mengdlmole.testtools.mock.model.MockDefinition;
import io.github.mengdlmole.testtools.mock.model.MockDefinition.AfterResponse;
import io.github.mengdlmole.testtools.mock.model.MockDefinition.CallbackRequest;
import io.github.mengdlmole.testtools.mock.model.MockDefinition.Request;
import io.github.mengdlmole.testtools.mock.model.MockDefinition.Response;
import io.github.mengdlmole.testtools.mock.model.MockDefinition.Retry;
import io.github.mengdlmole.testtools.security.SignContext;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable, completely validated Mock configuration visible to one request.
 *
 * @param version monotonically increasing successful reload version
 * @param loadedAt time at which this catalog became available
 * @param fingerprint SHA-256 fingerprint of definitions, files, variables, and secrets
 * @param signContext variables and secrets captured for this catalog
 * @param definitions validated Mock definitions with referenced files captured in memory
 */
public record MockCatalog(
    long version,
    Instant loadedAt,
    String fingerprint,
    SignContext signContext,
    List<LoadedMock> definitions) {
  public MockCatalog {
    definitions = List.copyOf(definitions);
  }

  /**
   * One Mock definition with all referenced files captured in the same catalog.
   *
   * @param definition declarative Mock definition
   * @param responseBodyFile captured response body file, or {@code null}
   * @param callbacks captured callback definitions
   */
  public record LoadedMock(
      MockDefinition definition, byte[] responseBodyFile, List<LoadedCallback> callbacks) {
    public LoadedMock {
      definition = copy(definition);
      responseBodyFile = copy(responseBodyFile);
      callbacks = List.copyOf(callbacks);
    }

    @Override
    public MockDefinition definition() {
      return copy(definition);
    }

    @Override
    public byte[] responseBodyFile() {
      return copy(responseBodyFile);
    }
  }

  /**
   * One callback definition with its optional body file captured in memory.
   *
   * @param definition declarative callback definition
   * @param bodyFile captured callback body file, or {@code null}
   */
  public record LoadedCallback(AfterResponse definition, byte[] bodyFile) {
    public LoadedCallback {
      definition = copy(definition);
      bodyFile = copy(bodyFile);
    }

    @Override
    public AfterResponse definition() {
      return copy(definition);
    }

    @Override
    public byte[] bodyFile() {
      return copy(bodyFile);
    }
  }

  private static byte[] copy(byte[] content) {
    return content == null ? null : content.clone();
  }

  private static MockDefinition copy(MockDefinition definition) {
    if (definition == null) {
      return null;
    }
    Request request = definition.request();
    Response response = definition.response();
    return new MockDefinition(
        definition.name(),
        definition.enabled(),
        definition.priority(),
        request == null
            ? null
            : new Request(
                request.method(),
                request.path(),
                copy(request.query()),
                copy(request.headers()),
                copy(request.body())),
        response == null
            ? null
            : new Response(
                response.status(),
                copy(response.headers()),
                copy(response.body()),
                response.bodyFile(),
                response.delayMs()),
        definition.securityHandler(),
        definition.afterResponse() == null
            ? null
            : definition.afterResponse().stream().map(MockCatalog::copy).toList());
  }

  private static AfterResponse copy(AfterResponse definition) {
    if (definition == null) {
      return null;
    }
    Retry retry = definition.retry();
    CallbackRequest request = definition.request();
    return new AfterResponse(
        definition.name(),
        definition.delayMs(),
        definition.timeoutMs(),
        definition.securityHandler(),
        retry == null ? null : new Retry(retry.maxAttempts(), retry.intervalMs()),
        request == null
            ? null
            : new CallbackRequest(
                request.method(),
                request.url(),
                copy(request.headers()),
                copy(request.body()),
                request.bodyFile()));
  }

  private static Map<String, String> copy(Map<String, String> values) {
    return values == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  private static com.fasterxml.jackson.databind.JsonNode copy(
      com.fasterxml.jackson.databind.JsonNode value) {
    return value == null ? null : value.deepCopy();
  }
}
