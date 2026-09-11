package io.github.mengdlmole.testtools.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
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
    @TempDir Path temporaryDirectory;

    @Test
    void buildsAndSignsFinalRequestAndMasksSignatureInLogs() {
        StubExecutor executor = new StubExecutor();
        List<String> logs = new ArrayList<>();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "hello");

        ApiTestClient.ApiResponse response = ApiTestClient.builder("http://localhost:8080/")
                .defaultHeader("x-test-header", "default")
                .executor(executor)
                .logger(logs::add)
                .build()
                .post("/orders")
                .header("X-Test-Header", "request")
                .query("name", "hello world")
                .query("name", "again")
                .query("apiToken", "query-secret")
                .jsonBody(body)
                .signWith(request -> request.header("X-Signature",
                        request.uri().getRawQuery() + request.bodyText()))
                .execute();

        assertEquals("name=hello+world&name=again&apiToken=query-secret", executor.request.uri().getRawQuery());
        assertEquals("request", executor.request.headers().get("X-Test-Header"));
        assertFalse(executor.request.headers().containsKey("x-test-header"));
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
                        io.github.mengdlmole.testtools.security.VerificationResult.failed("bad signature"));

        ApiTestClient.ResponseVerificationException error = assertThrows(
                ApiTestClient.ResponseVerificationException.class, request::executeVerified);
        assertEquals("bad signature", error.getMessage());
    }

    @Test
    void usesJsonFileBytesDirectlyAndAddsContentType() throws Exception {
        Path json = temporaryDirectory.resolve("request.json");
        Files.writeString(json, "{\n  \"message\": \"preserve whitespace\"\n}\n");
        StubExecutor executor = new StubExecutor();

        ApiTestClient.builder("http://localhost")
                .executor(executor)
                .logger(message -> {})
                .build()
                .post("/echo")
                .jsonBodyFile(json)
                .execute();

        assertEquals("{\n  \"message\": \"preserve whitespace\"\n}\n", executor.request.bodyText());
        assertEquals("application/json", executor.request.headers().get("Content-Type"));
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
