package io.github.mengdlmole.testtools.http;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MutableRequest {
    private final String method;
    private URI uri;
    private final Map<String, String> headers;
    private byte[] body;

    public MutableRequest(String method, URI uri, Map<String, String> headers, byte[] body) {
        this.method = method;
        this.uri = uri;
        this.headers = new LinkedHashMap<>(headers == null ? Map.of() : headers);
        this.body = body == null ? new byte[0] : body.clone();
    }

    public String method() { return method; }
    public URI uri() { return uri; }
    public void uri(URI uri) { this.uri = uri; }
    public Map<String, String> headers() { return headers; }
    public void header(String name, String value) {
        HttpHeaderSupport.putReplacingIgnoreCase(headers, name, value);
    }
    public byte[] body() { return body.clone(); }
    public String bodyText() { return new String(body, StandardCharsets.UTF_8); }
    public void body(byte[] body) { this.body = body == null ? new byte[0] : body.clone(); }
}
