package io.github.mengdlmole.testtools.project.security;

import io.github.mengdlmole.testtools.http.MutableRequest;
import io.github.mengdlmole.testtools.http.RequestSnapshot;
import io.github.mengdlmole.testtools.security.SecurityHandlerLoader;
import io.github.mengdlmole.testtools.security.SignContext;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignedHeaderBodyApiSecurityHandlerTest {
    @Test
    void projectHandlersAreDiscoveredWithoutCoreRegistration() {
        var handlers = SecurityHandlerLoader.create();

        assertEquals(DemoHmacSecurityHandler.class, handlers.byId("demoHmacSha256").getClass());
        assertEquals(QueryBodyHmacSecurityHandler.class, handlers.byId("queryBodyHmacSha256").getClass());
        assertEquals(SignedHeaderBodyApiSecurityHandler.class, handlers.byId("signedHeaderBodyApi").getClass());
    }

    @Test
    void assemblesThisApisSelectedFieldsInProtocolOrder() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("timestamp", "222");
        headers.put("tranId", "111");

        String result = SignedHeaderBodyApiSecurityHandler.buildSignData(
                headers, "{\"ignored\":true,\"name\":333}");

        assertEquals("tranId111timestamp222name333", result);
    }

    @Test
    void rejectsMissingRequiredFieldsInsteadOfSigningAmbiguousData() {
        assertThrows(IllegalArgumentException.class,
                () -> SignedHeaderBodyApiSecurityHandler.buildSignData(
                        Map.of("tranId", "111", "timestamp", "222"), "{}"));
    }

    @Test
    void rejectsUseByAnotherApi() {
        MutableRequest request = new MutableRequest("POST", URI.create("/another-api"),
                Map.of("tranId", "111", "timestamp", "222"),
                "{\"name\":333}".getBytes(StandardCharsets.UTF_8));
        SignContext context = new SignContext(Map.of(), Map.of("appKey", "key", "appSecret", "secret"));

        assertThrows(IllegalArgumentException.class,
                () -> new SignedHeaderBodyApiSecurityHandler().signRequest(context, request));
    }

    @Test
    void verifiesSignedRequestAndRejectsChangedAppKeyOrBody() {
        SignedHeaderBodyApiSecurityHandler handler = new SignedHeaderBodyApiSecurityHandler();
        SignContext context = new SignContext(Map.of(), Map.of("appKey", "key", "appSecret", "secret"));
        MutableRequest request = new MutableRequest("POST", URI.create("/signed/header-body"),
                Map.of("tranId", "111", "timestamp", "222"),
                "{\"name\":333}".getBytes(StandardCharsets.UTF_8));
        handler.signRequest(context, request);

        assertTrue(handler.verifyMockRequest(context, snapshot(request)).success());
        request.header("X-App-Key", "wrong");
        assertFalse(handler.verifyMockRequest(context, snapshot(request)).success());
        request.header("X-App-Key", "key");
        request.body("{\"name\":334}".getBytes(StandardCharsets.UTF_8));
        assertFalse(handler.verifyMockRequest(context, snapshot(request)).success());
    }

    @Test
    void reportsMissingSigningSecret() {
        MutableRequest request = new MutableRequest("POST", URI.create("/signed/header-body"),
                Map.of("tranId", "111", "timestamp", "222"),
                "{\"name\":333}".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> new SignedHeaderBodyApiSecurityHandler().signRequest(
                new SignContext(Map.of(), Map.of("appKey", "key")), request));
    }

    private RequestSnapshot snapshot(MutableRequest request) {
        return new RequestSnapshot(request.method(), request.uri(),
                request.headers().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> List.of(entry.getValue()))), request.body());
    }
}
