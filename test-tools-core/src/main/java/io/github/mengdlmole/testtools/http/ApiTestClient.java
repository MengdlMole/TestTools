package io.github.mengdlmole.testtools.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mengdlmole.testtools.http.transport.HttpExecutor;
import io.github.mengdlmole.testtools.http.transport.MutableRequest;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Creates explicit API requests and sends them through the shared HTTP executor.
 *
 * <p>The client deliberately does not own signing or assertions. Tests assemble headers, body,
 * signature data, and signatures before calling {@link #send(ApiRequest)}.
 *
 * @since 0.2.0
 */
public final class ApiTestClient {
  private final String baseUrl;
  private final Map<String, String> defaultHeaders;
  private final Duration defaultTimeout;
  private final ObjectMapper mapper;
  private final HttpExecutor executor;
  private final Consumer<String> logger;
  private final int bodyLogLimit;
  private final boolean maskSensitiveData;

  private ApiTestClient(Builder builder) {
    baseUrl = builder.baseUrl.trim();
    defaultHeaders = Map.copyOf(builder.defaultHeaders);
    defaultTimeout = builder.timeout;
    mapper = builder.mapper;
    executor =
        builder.executor == null ? new HttpExecutor(builder.maskSensitiveData) : builder.executor;
    logger = builder.logger;
    bodyLogLimit = builder.bodyLogLimit;
    maskSensitiveData = builder.maskSensitiveData;
  }

  /**
   * Starts configuring a client for one target base URL.
   *
   * @param baseUrl absolute HTTP or HTTPS base URL
   * @return client builder
   */
  public static Builder builder(String baseUrl) {
    return new Builder(baseUrl);
  }

  /**
   * Creates an unexecuted request using an arbitrary HTTP method.
   *
   * @param method HTTP method
   * @param pathOrUrl relative path or absolute URL
   * @return mutable request owned by the calling test
   */
  public ApiRequest request(String method, String pathOrUrl) {
    return new ApiRequest(baseUrl, method, pathOrUrl, defaultHeaders, defaultTimeout, mapper);
  }

  /**
   * Creates an unexecuted GET request.
   *
   * @param pathOrUrl relative path or absolute URL
   * @return mutable request
   */
  public ApiRequest get(String pathOrUrl) {
    return request("GET", pathOrUrl);
  }

  /**
   * Creates an unexecuted POST request.
   *
   * @param pathOrUrl relative path or absolute URL
   * @return mutable request
   */
  public ApiRequest post(String pathOrUrl) {
    return request("POST", pathOrUrl);
  }

  /**
   * Creates an unexecuted PUT request.
   *
   * @param pathOrUrl relative path or absolute URL
   * @return mutable request
   */
  public ApiRequest put(String pathOrUrl) {
    return request("PUT", pathOrUrl);
  }

  /**
   * Creates an unexecuted PATCH request.
   *
   * @param pathOrUrl relative path or absolute URL
   * @return mutable request
   */
  public ApiRequest patch(String pathOrUrl) {
    return request("PATCH", pathOrUrl);
  }

  /**
   * Creates an unexecuted DELETE request.
   *
   * @param pathOrUrl relative path or absolute URL
   * @return mutable request
   */
  public ApiRequest delete(String pathOrUrl) {
    return request("DELETE", pathOrUrl);
  }

  /**
   * Sends a fully assembled request and logs its request/response exchange.
   *
   * @param request request whose headers, body, and signature are already final
   * @return captured response and sent request snapshot
   */
  public ApiResponse send(ApiRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    MutableRequest httpRequest = request.toMutableRequest();

    log("--> " + httpRequest.method() + " " + uriForLog(httpRequest.uri()));
    if (!httpRequest.headers().isEmpty()) {
      log("    request headers: " + headersForLog(httpRequest.headers()));
    }
    if (httpRequest.body().length > 0) {
      log("    request body   : " + bodyForLog(httpRequest.bodyText()));
    }

    HttpExecutor.Exchange exchange = executor.execute(httpRequest, request.timeout());
    log(
        "<-- HTTP "
            + exchange.response().status()
            + " ("
            + exchange.response().durationMs()
            + " ms)");
    if (!exchange.response().headers().isEmpty()) {
      log("    response headers: " + headersForLog(flatten(exchange.response().headers())));
    }
    if (exchange.response().body().length > 0) {
      log("    response body   : " + bodyForLog(exchange.response().bodyText()));
    }
    return new ApiResponse(exchange.response(), mapper);
  }

  /**
   * Builds an {@link ApiTestClient}; most tests obtain one through {@code ApiTestSupport}.
   */
  public static final class Builder {
    private final String baseUrl;
    private final Map<String, String> defaultHeaders = new LinkedHashMap<>();
    private Duration timeout = Duration.ofSeconds(30);
    private ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private HttpExecutor executor;
    private Consumer<String> logger = System.out::println;
    private int bodyLogLimit = Integer.getInteger("testtools.logBodyLimit", 4000);
    private boolean maskSensitiveData = true;

    private Builder(String baseUrl) {
      if (baseUrl == null || baseUrl.isBlank()) {
        throw new IllegalArgumentException("Base URL is required");
      }
      URI parsed = URI.create(baseUrl.trim());
      if (!("http".equalsIgnoreCase(parsed.getScheme())
              || "https".equalsIgnoreCase(parsed.getScheme()))
          || parsed.getHost() == null) {
        throw new IllegalArgumentException("Base URL must be an absolute HTTP(S) URL: " + baseUrl);
      }
      this.baseUrl = baseUrl;
    }

    /**
     * Adds a Header copied into every request created by this client.
     *
     * @param name Header name
     * @param value Header value
     * @return this builder
     * @throws IllegalArgumentException if the Header name is blank
     */
    public Builder defaultHeader(String name, String value) {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Header name is required");
      }
      HttpHeaderSupport.putReplacingIgnoreCase(defaultHeaders, name, value == null ? "" : value);
      return this;
    }

    /**
     * Sets the default timeout used by requests.
     *
     * @param value positive timeout
     * @return this builder
     */
    public Builder timeout(Duration value) {
      timeout = positiveTimeout(value);
      return this;
    }

    /**
     * Sets the Jackson mapper used for request and response JSON.
     *
     * @param value object mapper
     * @return this builder
     */
    public Builder objectMapper(ObjectMapper value) {
      mapper = Objects.requireNonNull(value);
      return this;
    }

    Builder executor(HttpExecutor value) {
      executor = Objects.requireNonNull(value);
      return this;
    }

    Builder logger(Consumer<String> value) {
      logger = Objects.requireNonNull(value);
      return this;
    }

    /**
     * Sets the maximum number of response or request body characters written to logs.
     *
     * @param value maximum characters, or {@code -1} for no limit
     * @return this builder
     * @throws IllegalArgumentException if the value is less than {@code -1}
     */
    public Builder bodyLogLimit(int value) {
      if (value < -1) {
        throw new IllegalArgumentException("Body log limit must be -1 or greater");
      }
      bodyLogLimit = value;
      return this;
    }

    /**
     * Controls masking of credentials, sensitive headers, and query parameters in local logs.
     *
     * @param enabled {@code true} to mask sensitive data; {@code false} to log raw values
     * @return this builder
     */
    public Builder maskSensitiveData(boolean enabled) {
      maskSensitiveData = enabled;
      return this;
    }

    /**
     * Creates an immutable client configuration.
     *
     * @return configured client
     */
    public ApiTestClient build() {
      return new ApiTestClient(this);
    }
  }

  static Duration positiveTimeout(Duration value) {
    Duration timeout = Objects.requireNonNull(value);
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("Timeout must be positive");
    }
    return timeout;
  }

  private String bodyForLog(String value) {
    if (bodyLogLimit < 0 || value.length() <= bodyLogLimit) {
      return value;
    }
    return value.substring(0, bodyLogLimit) + "... [truncated, total " + value.length() + " chars]";
  }

  private void log(String value) {
    logger.accept(value);
  }

  private String uriForLog(URI uri) {
    return maskSensitiveData ? SensitiveDataMasker.maskUri(uri) : uri.toString();
  }

  private Map<String, String> headersForLog(Map<String, String> headers) {
    return maskSensitiveData ? SensitiveDataMasker.maskHeaders(headers) : headers;
  }

  private static Map<String, String> flatten(Map<String, List<String>> headers) {
    Map<String, String> result = new LinkedHashMap<>();
    headers.forEach((key, value) -> result.put(key, String.join(", ", value)));
    return result;
  }
}
