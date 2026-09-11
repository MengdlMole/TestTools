package io.github.mengdlmole.testtools.http;

import java.util.Map;

public final class HttpHeaderSupport {
    private HttpHeaderSupport() {}

    public static boolean containsIgnoreCase(Map<String, ?> headers, String name) {
        return headers.keySet().stream().anyMatch(name::equalsIgnoreCase);
    }

    public static void putReplacingIgnoreCase(Map<String, String> headers, String name, String value) {
        headers.keySet().removeIf(name::equalsIgnoreCase);
        headers.put(name, value);
    }

    public static void putIfAbsentIgnoreCase(Map<String, String> headers, String name, String value) {
        if (!containsIgnoreCase(headers, name)) headers.put(name, value);
    }
}
