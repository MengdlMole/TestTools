package io.github.mengdlmole.testtools.http;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared masking rules for local logs and persisted HTTP metadata.
 */
public final class SensitiveDataMasker {
  private SensitiveDataMasker() {}

  public static Map<String, String> maskHeaders(Map<String, String> headers) {
    Map<String, String> result = new LinkedHashMap<>(headers == null ? Map.of() : headers);
    result.replaceAll((key, value) -> isSensitiveName(key) ? "***" : value);
    return result;
  }

  public static String maskUri(URI uri) {
    return uri == null ? null : maskUri(uri.toString());
  }

  public static String maskUri(String uri) {
    if (uri == null) {
      return null;
    }
    String safeUri = maskUserInfo(uri);
    int queryStart = safeUri.indexOf('?');
    if (queryStart < 0) {
      return safeUri;
    }
    int fragmentStart = safeUri.indexOf('#', queryStart);
    String query =
        safeUri.substring(queryStart + 1, fragmentStart < 0 ? safeUri.length() : fragmentStart);
    String masked =
        Arrays.stream(query.split("&", -1))
            .map(SensitiveDataMasker::maskQueryPart)
            .collect(Collectors.joining("&"));
    return safeUri.substring(0, queryStart + 1)
        + masked
        + (fragmentStart < 0 ? "" : safeUri.substring(fragmentStart));
  }

  public static boolean isSensitiveName(String name) {
    String value = decode(name == null ? "" : name).toLowerCase();
    return value.contains("authorization")
        || value.contains("cookie")
        || value.contains("secret")
        || value.contains("signature")
        || value.contains("api-key")
        || value.contains("apikey")
        || value.contains("app-key")
        || value.contains("appkey")
        || value.contains("token");
  }

  private static String maskQueryPart(String item) {
    int separator = item.indexOf('=');
    String name = separator < 0 ? item : item.substring(0, separator);
    return isSensitiveName(name) && separator >= 0 ? name + "=***" : item;
  }

  private static String maskUserInfo(String uri) {
    int authorityStart;
    int scheme = uri.indexOf("://");
    if (scheme >= 0) {
      authorityStart = scheme + 3;
    } else if (uri.startsWith("//")) {
      authorityStart = 2;
    } else {
      return uri;
    }
    int authorityEnd = uri.length();
    for (char delimiter : new char[] {'/', '?', '#'}) {
      int position = uri.indexOf(delimiter, authorityStart);
      if (position >= 0) {
        authorityEnd = Math.min(authorityEnd, position);
      }
    }
    int separator = uri.lastIndexOf('@', authorityEnd - 1);
    if (separator < authorityStart) {
      return uri;
    }
    return uri.substring(0, authorityStart) + "***@" + uri.substring(separator + 1);
  }

  private static String decode(String value) {
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException ignored) {
      return value;
    }
  }
}
