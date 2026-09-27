package io.github.mengdlmole.testtools.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.github.mengdlmole.testtools.http.transport.ResponseSnapshot;

/**
 * HTTP response convenience view returned to JUnit assertions.
 *
 * @since 0.2.0
 */
public final class ApiResponse {
  private final ResponseSnapshot response;
  private final ObjectMapper mapper;

  ApiResponse(ResponseSnapshot response, ObjectMapper mapper) {
    this.response = response;
    this.mapper = mapper;
  }

  /**
   * Returns the HTTP status code.
   *
   * @return response status
   */
  public int status() {
    return response.status();
  }

  /**
   * Returns the measured request duration.
   *
   * @return duration in milliseconds
   */
  public long durationMs() {
    return response.durationMs();
  }

  /**
   * Returns the response body interpreted as UTF-8 text.
   *
   * @return response body
   */
  public String body() {
    return response.bodyText();
  }

  /**
   * Returns the first response Header value matching the name without regard to case.
   *
   * @param name Header name
   * @return first Header value, or {@code null} when absent
   */
  public String header(String name) {
    return response.firstHeader(name);
  }

  /**
   * Parses the response body as JSON.
   *
   * @return parsed JSON tree
   * @throws IllegalArgumentException if the body is not valid JSON
   */
  public JsonNode json() {
    try {
      JsonNode result = mapper.readTree(response.body());
      if (result == null || result.isMissingNode()) {
        throw new IllegalArgumentException("Response body is empty; expected JSON");
      }
      return result;
    } catch (IllegalArgumentException error) {
      throw error;
    } catch (Exception error) {
      throw new IllegalArgumentException("Response is not valid JSON", error);
    }
  }

  /**
   * Evaluates a JsonPath expression against the response body.
   *
   * @param path JsonPath expression
   * @return value produced by JsonPath
   */
  public Object jsonPath(String path) {
    return JsonPath.read(body(), path);
  }
}
