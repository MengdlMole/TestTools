package io.github.localtools.testtools.http;

import java.util.Map;

public final class HttpHeaderSupport {
    private HttpHeaderSupport() {}

    public static void putIfAbsentIgnoreCase(Map<String, String> headers, String name, String value) {
        boolean present = headers.keySet().stream().anyMatch(name::equalsIgnoreCase);
        if (!present) headers.put(name, value);
    }
}
