package io.github.localtools.testtools.http;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public record RequestSnapshot(String method, URI uri, Map<String, List<String>> headers, byte[] body) {
    public RequestSnapshot {
        headers = immutableHeaders(headers);
        body = body == null ? new byte[0] : body.clone();
    }

    @Override public byte[] body() { return body.clone(); }
    public String bodyText() { return new String(body, StandardCharsets.UTF_8); }
    public String firstHeader(String name) {
        return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .findFirst().flatMap(entry -> entry.getValue().stream().findFirst()).orElse(null);
    }

    private static Map<String, List<String>> immutableHeaders(Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) return Map.of();
        java.util.LinkedHashMap<String, List<String>> copy = new java.util.LinkedHashMap<>();
        source.forEach((name, values) -> copy.put(name, values == null ? List.of() : List.copyOf(values)));
        return java.util.Collections.unmodifiableMap(copy);
    }
}
