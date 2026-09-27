package io.github.mengdlmole.testtools.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mengdlmole.testtools.http.transport.MutableRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mutable request data assembled explicitly by one API test before it is sent.
 *
 * @since 0.2.0
 */
public final class ApiRequest {
  private final String baseUrl;
  private final String method;
  private final String pathOrUrl;
  private final ObjectMapper mapper;
  private final List<HttpRequestUriBuilder.QueryParameter> query = new ArrayList<>();
  private final Map<String, String> headers;
  private byte[] body = new byte[0];
  private Duration timeout;

  ApiRequest(
      String baseUrl,
      String method,
      String pathOrUrl,
      Map<String, String> defaultHeaders,
      Duration defaultTimeout,
      ObjectMapper mapper) {
    if (method == null || method.isBlank()) {
      throw new IllegalArgumentException("HTTP method is required");
    }
    if (pathOrUrl == null || pathOrUrl.isBlank()) {
      throw new IllegalArgumentException("Path or URL is required");
    }
    this.baseUrl = baseUrl;
    this.method = method.toUpperCase(Locale.ROOT);
    this.pathOrUrl = pathOrUrl;
    this.mapper = mapper;
    headers = new LinkedHashMap<>(defaultHeaders);
    timeout = defaultTimeout;
  }

  /**
   * Returns the HTTP method selected when this request was created.
   *
   * @return uppercase HTTP method
   */
  public String method() {
    return method;
  }

  /**
   * Returns the final URI, including all query parameters added so far.
   *
   * @return current absolute request URI
   */
  public URI uri() {
    return HttpRequestUriBuilder.build(baseUrl, pathOrUrl, query);
  }

  /**
   * Appends one query parameter while preserving insertion order and duplicate names.
   *
   * @param name parameter name
   * @param value parameter value; {@code null} becomes an empty value
   */
  public void query(String name, Object value) {
    query.add(
        new HttpRequestUriBuilder.QueryParameter(name, value == null ? "" : String.valueOf(value)));
  }

  /**
   * Sets or replaces one request Header, matching its name without regard to case.
   *
   * @param name Header name
   * @param value Header value; {@code null} becomes an empty value
   * @throws IllegalArgumentException if the Header name is blank
   */
  public void header(String name, Object value) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Header name is required");
    }
    HttpHeaderSupport.putReplacingIgnoreCase(
        headers, name, value == null ? "" : String.valueOf(value));
  }

  /**
   * Returns one currently configured Header value without regard to name case.
   *
   * @param name Header name
   * @return Header value, or {@code null} when absent
   */
  public String header(String name) {
    return headers.entrySet().stream()
        .filter(entry -> entry.getKey().equalsIgnoreCase(name))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  /**
   * Returns a read-only view of the current single-value Headers.
   *
   * @return current Headers
   */
  public Map<String, String> headers() {
    return Collections.unmodifiableMap(headers);
  }

  /**
   * Replaces the body with UTF-8 text.
   *
   * @param value body text; {@code null} clears the body
   */
  public void body(String value) {
    body = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Replaces the body with a defensive copy of the supplied bytes.
   *
   * @param value body bytes; {@code null} clears the body
   */
  public void body(byte[] value) {
    body = value == null ? new byte[0] : value.clone();
  }

  /**
   * Reads body bytes verbatim from a file.
   *
   * @param file body file
   * @throws IllegalArgumentException if the file cannot be read
   */
  public void bodyFile(Path file) {
    try {
      body(Files.readAllBytes(file));
    } catch (Exception error) {
      throw new IllegalArgumentException("Cannot read request body: " + file, error);
    }
  }

  /**
   * Serializes one value as JSON and adds a JSON Content-Type when absent.
   *
   * @param value value accepted by the configured Jackson mapper
   * @throws IllegalArgumentException if the value cannot be serialized
   */
  public void jsonBody(Object value) {
    try {
      body = value == null ? new byte[0] : mapper.writeValueAsBytes(value);
      if (body.length > 0) {
        HttpHeaderSupport.putIfAbsentIgnoreCase(headers, "Content-Type", "application/json");
      }
    } catch (Exception error) {
      throw new IllegalArgumentException("Cannot serialize JSON request body", error);
    }
  }

  /**
   * Uses the JSON file bytes verbatim so signatures can include the exact persisted body text.
   *
   * @param file JSON fixture to read
   * @throws IllegalArgumentException if the file cannot be read or does not contain JSON
   */
  public void jsonBodyFile(Path file) {
    try {
      byte[] content = Files.readAllBytes(file);
      JsonNode parsed = mapper.readTree(content);
      if (parsed == null) {
        throw new IllegalArgumentException("JSON request body must not be empty: " + file);
      }
      body = content;
      HttpHeaderSupport.putIfAbsentIgnoreCase(headers, "Content-Type", "application/json");
    } catch (IllegalArgumentException error) {
      throw error;
    } catch (Exception error) {
      throw new IllegalArgumentException("Cannot read JSON request body: " + file, error);
    }
  }

  /**
   * Returns a defensive copy of the final body bytes.
   *
   * @return current body bytes
   */
  public byte[] body() {
    return body.clone();
  }

  /**
   * Returns the final body interpreted as UTF-8 text.
   *
   * @return current UTF-8 body
   */
  public String bodyText() {
    return new String(body, StandardCharsets.UTF_8);
  }

  /**
   * Overrides the client timeout for this request.
   *
   * @param value positive request timeout
   */
  public void timeout(Duration value) {
    timeout = ApiTestClient.positiveTimeout(value);
  }

  Duration timeout() {
    return timeout;
  }

  MutableRequest toMutableRequest() {
    return new MutableRequest(method, uri(), headers, body);
  }
}
