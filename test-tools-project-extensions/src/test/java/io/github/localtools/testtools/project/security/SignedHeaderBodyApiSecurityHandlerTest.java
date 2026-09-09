package io.github.localtools.testtools.project.security;

import io.github.localtools.testtools.http.HttpModels.MutableRequest;
import io.github.localtools.testtools.security.DefaultSecurityHandlers;
import io.github.localtools.testtools.security.SignContext;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignedHeaderBodyApiSecurityHandlerTest {
    @Test
    void isDiscoveredAsAProjectExtensionWithoutCoreRegistration() {
        assertEquals(SignedHeaderBodyApiSecurityHandler.class,
                DefaultSecurityHandlers.create().byId("signedHeaderBodyApi").getClass());
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
}
