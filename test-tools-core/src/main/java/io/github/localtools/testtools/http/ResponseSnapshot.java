package io.github.localtools.testtools.http;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public record ResponseSnapshot(int status, Map<String, List<String>> headers, byte[] body, long durationMs) {
    public String bodyText() { return new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8); }
    public String firstHeader(String name) {
        return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .findFirst().flatMap(entry -> entry.getValue().stream().findFirst()).orElse(null);
    }
}
