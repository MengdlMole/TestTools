package io.github.localtools.testtools.security;

import io.github.localtools.testtools.http.HttpModels.MutableRequest;
import io.github.localtools.testtools.http.HttpModels.MutableResponse;
import io.github.localtools.testtools.http.HttpModels.RequestSnapshot;
import io.github.localtools.testtools.http.HttpModels.ResponseSnapshot;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DemoHmacSecurityHandlerTest {
    private final DemoHmacSecurityHandler handler = new DemoHmacSecurityHandler();
    private final SignContext context = new SignContext(Map.of(), Map.of("appKey", "app", "appSecret", "secret"));

    @Test
    void signsAndVerifiesBothDirections() {
        MutableRequest outbound = new MutableRequest("POST", URI.create("http://localhost/signed/echo"),
                Map.of(), "{}".getBytes(StandardCharsets.UTF_8));
        handler.signRequest(context, outbound);
        RequestSnapshot request = new RequestSnapshot(outbound.method(), outbound.uri(),
                outbound.headers().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> List.of(entry.getValue()))), outbound.body());
        assertThat(handler.verifyMockRequest(context, request).success()).isTrue();

        MutableResponse mutableResponse = new MutableResponse(200, Map.of(), "{}".getBytes(StandardCharsets.UTF_8));
        handler.signMockResponse(context, request, mutableResponse);
        ResponseSnapshot response = new ResponseSnapshot(200,
                mutableResponse.headers().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> List.of(entry.getValue()))), mutableResponse.body(), 1);
        assertThat(handler.verifyResponse(context, request, response).success()).isTrue();
    }
}
