package io.github.mengdlmole.testtools.http;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Shared masking rules for local logs and persisted HTTP metadata. */
public final class SensitiveDataMasker {
    private SensitiveDataMasker() {}

    public static Map<String, String> maskHeaders(Map<String, String> headers) {
        Map<String, String> result = new LinkedHashMap<>(headers == null ? Map.of() : headers);
        result.replaceAll((key, value) -> isSensitiveName(key) ? "***" : value);
        return result;
    }

    public static String maskUri(URI uri) { return uri == null ? null : maskUri(uri.toString()); }

    public static String maskUri(String uri) {
        if (uri == null) return null;
        int queryStart = uri.indexOf('?');
        if (queryStart < 0) return uri;
        int fragmentStart = uri.indexOf('#', queryStart);
        String query = uri.substring(queryStart + 1, fragmentStart < 0 ? uri.length() : fragmentStart);
        String masked = Arrays.stream(query.split("&", -1)).map(SensitiveDataMasker::maskQueryPart)
                .collect(Collectors.joining("&"));
        return uri.substring(0, queryStart + 1) + masked
                + (fragmentStart < 0 ? "" : uri.substring(fragmentStart));
    }

    public static boolean isSensitiveName(String name) {
        String value = decode(name == null ? "" : name).toLowerCase();
        return value.contains("authorization") || value.contains("cookie") || value.contains("secret")
                || value.contains("signature") || value.contains("api-key") || value.contains("apikey")
                || value.contains("app-key") || value.contains("appkey") || value.contains("token");
    }

    private static String maskQueryPart(String item) {
        int separator = item.indexOf('=');
        String name = separator < 0 ? item : item.substring(0, separator);
        return isSensitiveName(name) && separator >= 0 ? name + "=***" : item;
    }

    private static String decode(String value) {
        try { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
        catch (IllegalArgumentException ignored) { return value; }
    }
}
