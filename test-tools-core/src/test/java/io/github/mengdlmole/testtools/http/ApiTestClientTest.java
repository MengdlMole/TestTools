package io.github.mengdlmole.testtools.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mengdlmole.testtools.http.transport.HttpExecutor;
import io.github.mengdlmole.testtools.http.transport.MutableRequest;
import io.github.mengdlmole.testtools.http.transport.RequestSnapshot;
import io.github.mengdlmole.testtools.http.transport.ResponseSnapshot;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiTestClientTest {
  @TempDir Path temporaryDirectory;

  @Test
  void sendsAnExplicitlyAssembledRequestAndMasksSecretsInLogs() {
    StubExecutor executor = new StubExecutor();
    List<String> logs = new ArrayList<>();
    ApiTestClient client =
        ApiTestClient.builder("http://localhost:8080/")
            .defaultHeader("x-test-header", "default")
            .executor(executor)
            .logger(logs::add)
            .build();

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("message", "hello");
    ApiRequest request = client.post("/orders");
    request.header("X-Test-Header", "request");
    request.query("name", "hello world");
    request.query("name", "again");
    request.query("apiToken", "query-secret");
    request.jsonBody(body);
    request.header("X-Signature", request.uri().getRawQuery() + request.bodyText());

    ApiResponse response = client.send(request);

    assertEquals(
        "name=hello+world&name=again&apiToken=query-secret", executor.request.uri().getRawQuery());
    assertEquals("request", executor.request.headers().get("X-Test-Header"));
    assertFalse(executor.request.headers().containsKey("x-test-header"));
    assertEquals(
        "name=hello+world&name=again&apiToken=query-secret{\"message\":\"hello\"}",
        executor.request.headers().get("X-Signature"));
    assertEquals(201, response.status());
    assertEquals("created", response.jsonPath("$.status"));
    assertFalse(String.join("\n", logs).contains("query-secret"));
  }

  @Test
  void usesJsonFileBytesDirectlyAndAddsContentType() throws Exception {
    Path json = temporaryDirectory.resolve("request.json");
    Files.writeString(json, "{\n  \"message\": \"preserve whitespace\"\n}\n");
    StubExecutor executor = new StubExecutor();
    ApiTestClient client =
        ApiTestClient.builder("http://localhost").executor(executor).logger(message -> {}).build();

    ApiRequest request = client.post("/echo");
    request.jsonBodyFile(json);
    client.send(request);

    assertEquals("{\n  \"message\": \"preserve whitespace\"\n}\n", executor.request.bodyText());
    assertEquals("application/json", executor.request.headers().get("Content-Type"));
  }

  @Test
  void canExplicitlyDisableMaskingForLocalDebugLogs() {
    List<String> logs = new ArrayList<>();
    ApiTestClient client =
        ApiTestClient.builder("http://user:password@localhost")
            .executor(new StubExecutor())
            .logger(logs::add)
            .maskSensitiveData(false)
            .build();
    ApiRequest request = client.get("/inspect");
    request.query("token", "raw-token");
    request.header("Authorization", "Bearer raw-token");

    client.send(request);

    String output = String.join("\n", logs);
    org.junit.jupiter.api.Assertions.assertTrue(output.contains("user:password"));
    org.junit.jupiter.api.Assertions.assertTrue(output.contains("Bearer raw-token"));
    org.junit.jupiter.api.Assertions.assertTrue(output.contains("token=raw-token"));
  }

  @Test
  void rejectsEmptyResponseWhenJsonIsRequested() {
    ApiResponse response =
        new ApiResponse(new ResponseSnapshot(204, Map.of(), new byte[0], 1), new ObjectMapper());

    assertThrows(IllegalArgumentException.class, response::json);
  }

  private static final class StubExecutor extends HttpExecutor {
    private MutableRequest request;

    @Override
    public Exchange execute(MutableRequest request, Duration timeout) {
      this.request = request;
      RequestSnapshot requestSnapshot =
          new RequestSnapshot(
              request.method(),
              request.uri(),
              request.headers().entrySet().stream()
                  .collect(
                      java.util.stream.Collectors.toMap(
                          Map.Entry::getKey, entry -> List.of(entry.getValue()))),
              request.body());
      byte[] body = "{\"status\":\"created\"}".getBytes(StandardCharsets.UTF_8);
      return new Exchange(
          requestSnapshot,
          new ResponseSnapshot(201, Map.of("Content-Type", List.of("application/json")), body, 12));
    }
  }
}
