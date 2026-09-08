package io.github.localtools.testtools.http;

import io.github.localtools.testtools.http.HttpModels.MutableRequest;
import io.github.localtools.testtools.http.HttpModels.RequestSnapshot;
import io.github.localtools.testtools.http.HttpModels.ResponseSnapshot;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiTestClientTest {
    @Test
    void buildsAndSignsFinalRequestAndMasksSignatureInLogs() {
        StubExecutor executor = new StubExecutor();
        List<String> logs = new ArrayList<>();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "hello");

        ApiTestClient.ApiResponse response = ApiTestClient.builder("http://localhost:8080/")
                .executor(executor)
                .logger(logs::add)
                .build()
                .post("/orders")
                .query("name", "hello world")
                .query("name", "again")
                .query("apiToken", "query-secret")
                .jsonBody(body)
                .signWith(request -> request.header("X-Signature",
                        request.uri().getRawQuery() + request.bodyText()))
                .execute();

        assertEquals("name=hello+world&name=again&apiToken=query-secret", executor.request.uri().getRawQuery());
        assertEquals("name=hello+world&name=again&apiToken=query-secret{\"message\":\"hello\"}",
                executor.request.headers().get("X-Signature"));
        assertEquals(201, response.status());
        assertEquals("created", response.jsonPath("$.status"));
        assertFalse(String.join("\n", logs).contains("query-secret"));
    }

    @Test
    void executeVerifiedFailsTheTestWhenResponseVerificationFails() {
        ApiTestClient.Request request = ApiTestClient.builder("http://localhost")
                .executor(new StubExecutor())
                .logger(message -> {})
                .build()
                .get("/health")
                .verifyWith((sent, response) ->
                        io.github.localtools.testtools.security.VerificationResult.failed("bad signature"));

        ApiTestClient.ResponseVerificationException error = assertThrows(
                ApiTestClient.ResponseVerificationException.class, request::executeVerified);
        assertEquals("bad signature", error.getMessage());
    }

    private static final class StubExecutor extends HttpExecutor {
        private MutableRequest request;

        @Override
        public Exchange execute(MutableRequest request, Duration timeout) {
            this.request = request;
            RequestSnapshot requestSnapshot = new RequestSnapshot(request.method(), request.uri(),
                    request.headers().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey, entry -> List.of(entry.getValue()))), request.body());
            byte[] body = "{\"status\":\"created\"}".getBytes(StandardCharsets.UTF_8);
            return new Exchange(requestSnapshot,
                    new ResponseSnapshot(201, Map.of("Content-Type", List.of("application/json")), body, 12));
        }
    }
}
