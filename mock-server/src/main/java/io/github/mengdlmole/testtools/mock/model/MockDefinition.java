package io.github.mengdlmole.testtools.mock.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/**
 * Complete declarative definition of one local HTTP stub.
 *
 * @param name unique mock definition name
 * @param enabled whether the definition participates in matching
 * @param priority lower values are matched first
 * @param request request-matching rules
 * @param response response returned for a match
 * @param securityHandler handler used to verify the request and sign the response
 * @param afterResponse callbacks dispatched after the response is returned
 */
public record MockDefinition(
    String name,
    Boolean enabled,
    Integer priority,
    Request request,
    Response response,
    String securityHandler,
    List<AfterResponse> afterResponse) {
  public record Request(
      String method,
      String path,
      Map<String, String> query,
      Map<String, String> headers,
      JsonNode body) {}

  public record Response(
      Integer status, Map<String, String> headers, JsonNode body, String bodyFile, Long delayMs) {}

  public record AfterResponse(
      String name,
      Long delayMs,
      Long timeoutMs,
      String securityHandler,
      Retry retry,
      CallbackRequest request) {}

  public record Retry(Integer maxAttempts, Long intervalMs) {}

  public record CallbackRequest(
      String method, String url, Map<String, String> headers, JsonNode body, String bodyFile) {}
}
