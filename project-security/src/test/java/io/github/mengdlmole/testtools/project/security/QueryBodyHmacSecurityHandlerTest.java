package io.github.mengdlmole.testtools.project.security;

import io.github.mengdlmole.testtools.http.MutableRequest;
import io.github.mengdlmole.testtools.http.RequestSnapshot;
import io.github.mengdlmole.testtools.security.SignContext;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryBodyHmacSecurityHandlerTest {
    @Test
    void usesEncodedQueryPairsInSortedOrderThenAppendsExactBody() {
        String actual = QueryBodyHmacSecurityHandler.buildSignData(
                URI.create("http://localhost/orders?z=last&name=a+b&name=a%2Bb"),
                "{\"quantity\":2}");

        assertEquals("name=a%2Bb&name=a+b&z=last{\"quantity\":2}", actual);
    }

    @Test
    void verifiesSignedRequestAndRejectsChangedAuthenticationData() {
        QueryBodyHmacSecurityHandler handler = new QueryBodyHmacSecurityHandler();
        SignContext context = new SignContext(Map.of(), Map.of("appKey", "app", "appSecret", "secret"));
        MutableRequest request = new MutableRequest("POST", URI.create("http://localhost/orders?name=one"),
                Map.of(), "{}".getBytes(StandardCharsets.UTF_8));
        handler.signRequest(context, request);

        assertTrue(handler.verifyMockRequest(context, snapshot(request)).success());
        request.header("X-App-Key", "wrong");
        assertFalse(handler.verifyMockRequest(context, snapshot(request)).success());
        request.header("X-App-Key", "app");
        request.body("changed".getBytes(StandardCharsets.UTF_8));
        assertFalse(handler.verifyMockRequest(context, snapshot(request)).success());
    }

    @Test
    void reportsMissingSigningSecret() {
        MutableRequest request = new MutableRequest("POST", URI.create("http://localhost/orders"),
                Map.of(), new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> new QueryBodyHmacSecurityHandler().signRequest(
                new SignContext(Map.of(), Map.of("appKey", "app")), request));
    }

    private RequestSnapshot snapshot(MutableRequest request) {
        return new RequestSnapshot(request.method(), request.uri(),
                request.headers().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> List.of(entry.getValue()))), request.body());
    }
}
