package io.github.localtools.testtools.http;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HttpModels {
    private HttpModels() {}

    public static final class MutableRequest {
        private final String method;
        private URI uri;
        private final Map<String, String> headers;
        private byte[] body;

        public MutableRequest(String method, URI uri, Map<String, String> headers, byte[] body) {
            this.method = method;
            this.uri = uri;
            this.headers = new LinkedHashMap<>(headers == null ? Map.of() : headers);
            this.body = body == null ? new byte[0] : body;
        }
        public String method() { return method; }
        public URI uri() { return uri; }
        public void uri(URI uri) { this.uri = uri; }
        public Map<String, String> headers() { return headers; }
        public void header(String name, String value) { headers.put(name, value); }
        public byte[] body() { return body; }
        public String bodyText() { return new String(body, StandardCharsets.UTF_8); }
        public void body(byte[] body) { this.body = body == null ? new byte[0] : body; }
    }

    public static final class MutableResponse {
        private int status;
        private final Map<String, String> headers;
        private byte[] body;

        public MutableResponse(int status, Map<String, String> headers, byte[] body) {
            this.status = status;
            this.headers = new LinkedHashMap<>(headers == null ? Map.of() : headers);
            this.body = body == null ? new byte[0] : body;
        }
        public int status() { return status; }
        public void status(int status) { this.status = status; }
        public Map<String, String> headers() { return headers; }
        public void header(String name, String value) { headers.put(name, value); }
        public byte[] body() { return body; }
        public String bodyText() { return new String(body, StandardCharsets.UTF_8); }
        public void body(byte[] body) { this.body = body == null ? new byte[0] : body; }
    }

    public record RequestSnapshot(String method, URI uri, Map<String, List<String>> headers, byte[] body) {
        public String bodyText() { return new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8); }
        public String firstHeader(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                    .findFirst().flatMap(e -> e.getValue().stream().findFirst()).orElse(null);
        }
    }

    public record ResponseSnapshot(int status, Map<String, List<String>> headers, byte[] body, long durationMs) {
        public String bodyText() { return new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8); }
        public String firstHeader(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                    .findFirst().flatMap(e -> e.getValue().stream().findFirst()).orElse(null);
        }
    }
}
