package io.github.localtools.testtools.http;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MutableResponse {
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
    public void header(String name, String value) {
        headers.keySet().removeIf(key -> key.equalsIgnoreCase(name));
        headers.put(name, value);
    }
    public byte[] body() { return body; }
    public String bodyText() { return new String(body, StandardCharsets.UTF_8); }
    public void body(byte[] body) { this.body = body == null ? new byte[0] : body; }
}
