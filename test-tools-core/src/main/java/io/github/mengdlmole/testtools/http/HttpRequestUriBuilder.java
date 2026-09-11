package io.github.mengdlmole.testtools.http;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Shared URL and query assembly for HTTP-based test runners. */
public final class HttpRequestUriBuilder {
    private HttpRequestUriBuilder() {}

    public static URI build(String baseUrl, String pathOrUrl, List<QueryParameter> query) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            throw new IllegalArgumentException("Path or URL is required");
        }
        String target = pathOrUrl.trim();
        String url = isAbsoluteHttpUrl(target)
                ? target
                : stripTrailingSlash(baseUrl) + (target.startsWith("/") ? target : "/" + target);

        if (query != null && !query.isEmpty()) {
            int fragmentIndex = url.indexOf('#');
            String fragment = fragmentIndex < 0 ? "" : url.substring(fragmentIndex);
            String withoutFragment = fragmentIndex < 0 ? url : url.substring(0, fragmentIndex);
            String separator = withoutFragment.contains("?") ? "&" : "?";
            String queryString = query.stream()
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
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Base URL is required");
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public record QueryParameter(String name, String value) {
        public QueryParameter {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Query name is required");
            value = value == null ? "" : value;
        }
    }
}
