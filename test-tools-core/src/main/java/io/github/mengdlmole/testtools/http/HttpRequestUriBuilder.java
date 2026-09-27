package io.github.mengdlmole.testtools.http;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Shared URL and query assembly for the JUnit client and Mock callbacks.
 *
 * @since 0.2.0
 */
public final class HttpRequestUriBuilder {
  private HttpRequestUriBuilder() {}

  /**
   * Builds an absolute HTTP URI and appends the supplied query parameters in iteration order.
   *
   * @param baseUrl base URL used when {@code pathOrUrl} is relative
   * @param pathOrUrl absolute URL or path relative to {@code baseUrl}
   * @param query query parameters to append; may be {@code null}
   * @return assembled request URI
   * @throws IllegalArgumentException if {@code pathOrUrl} is blank or a parameter name is blank
   */
  public static URI build(String baseUrl, String pathOrUrl, List<QueryParameter> query) {
    if (pathOrUrl == null || pathOrUrl.isBlank()) {
      throw new IllegalArgumentException("Path or URL is required");
    }
    String target = pathOrUrl.trim();
    String url =
        isAbsoluteHttpUrl(target)
            ? target
            : stripTrailingSlash(baseUrl) + (target.startsWith("/") ? target : "/" + target);

    if (query != null && !query.isEmpty()) {
      int fragmentIndex = url.indexOf('#');
      String fragment = fragmentIndex < 0 ? "" : url.substring(fragmentIndex);
      String withoutFragment = fragmentIndex < 0 ? url : url.substring(0, fragmentIndex);
      String separator = withoutFragment.contains("?") ? "&" : "?";
      String queryString =
          query.stream()
              .map(entry -> encode(entry.name()) + "=" + encode(entry.value()))
              .collect(java.util.stream.Collectors.joining("&"));
      url = withoutFragment + separator + queryString + fragment;
    }
    return URI.create(url);
  }

  private static boolean isAbsoluteHttpUrl(String value) {
    return value.regionMatches(true, 0, "http://", 0, 7)
        || value.regionMatches(true, 0, "https://", 0, 8);
  }

  private static String stripTrailingSlash(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Base URL is required");
    }
    String result = value.trim();
    while (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }

  /**
   * One query parameter before URL encoding.
   *
   * @param name non-blank parameter name
   * @param value parameter value; {@code null} is encoded as an empty value
   */
  public record QueryParameter(String name, String value) {
    public QueryParameter {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Query name is required");
      }
      value = value == null ? "" : value;
    }
  }
}
