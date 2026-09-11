package io.github.mengdlmole.testtools.project.security;

import io.github.mengdlmole.testtools.http.MutableRequest;
import io.github.mengdlmole.testtools.http.MutableResponse;
import io.github.mengdlmole.testtools.http.RequestSnapshot;
import io.github.mengdlmole.testtools.http.ResponseSnapshot;
import io.github.mengdlmole.testtools.security.SignContext;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DemoHmacSecurityHandlerTest {
    private final DemoHmacSecurityHandler handler = new DemoHmacSecurityHandler();
    private final SignContext context = new SignContext(Map.of(),
            Map.of("appKey", "app", "appSecret", "secret"));

    @Test
    void signsAndVerifiesBothDirections() {
        MutableRequest outbound = new MutableRequest("POST", URI.create("http://localhost/signed/echo"),
                Map.of(), "{}".getBytes(StandardCharsets.UTF_8));
        handler.signRequest(context, outbound);
        RequestSnapshot request = new RequestSnapshot(outbound.method(), outbound.uri(),
                outbound.headers().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> List.of(entry.getValue()))), outbound.body());
        assertTrue(handler.verifyMockRequest(context, request).success());

        MutableResponse mutableResponse = new MutableResponse(200, Map.of(),
                "{}".getBytes(StandardCharsets.UTF_8));
        handler.signMockResponse(context, request, mutableResponse);
        ResponseSnapshot response = new ResponseSnapshot(200,
                mutableResponse.headers().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> List.of(entry.getValue()))), mutableResponse.body(), 1);
        assertTrue(handler.verifyResponse(context, request, response).success());
    }

    @Test
    void rejectsChangedAppKeyBodyAndTimestamp() {
        MutableRequest outbound = signedRequest();

        outbound.header("X-App-Key", "wrong");
        assertFalse(handler.verifyMockRequest(context, snapshot(outbound)).success());

        outbound = signedRequest();
        outbound.body("changed".getBytes(StandardCharsets.UTF_8));
        assertFalse(handler.verifyMockRequest(context, snapshot(outbound)).success());

        outbound = signedRequest();
        outbound.header("X-Timestamp", "0");
        assertFalse(handler.verifyMockRequest(context, snapshot(outbound)).success());
    }

    @Test
    void reportsMissingSigningSecret() {
        MutableRequest request = new MutableRequest("POST", URI.create("http://localhost/signed/echo"),
                Map.of(), new byte[0]);
        assertThrows(IllegalArgumentException.class,
                () -> handler.signRequest(new SignContext(Map.of(), Map.of("appKey", "app")), request));
    }

    private MutableRequest signedRequest() {
        MutableRequest request = new MutableRequest("POST", URI.create("http://localhost/signed/echo"),
                Map.of(), "{}".getBytes(StandardCharsets.UTF_8));
        handler.signRequest(context, request);
        return request;
    }

    private RequestSnapshot snapshot(MutableRequest request) {
        return new RequestSnapshot(request.method(), request.uri(),
                request.headers().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> List.of(entry.getValue()))), request.body());
    }
}
