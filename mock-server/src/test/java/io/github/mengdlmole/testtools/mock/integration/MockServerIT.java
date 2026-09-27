package io.github.mengdlmole.testtools.mock.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mengdlmole.testtools.http.ApiResponse;
import io.github.mengdlmole.testtools.http.ApiTestClient;
import io.github.mengdlmole.testtools.mock.MockServerApplication;
import io.github.mengdlmole.testtools.mock.config.MockDefinitionRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    classes = MockServerApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "test-tools.mock-reload-interval-ms=60000")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MockServerIT {
  private static final Path WORKSPACE = createWorkspace();

  @DynamicPropertySource
  static void workspaceProperty(DynamicPropertyRegistry properties) {
    properties.add("test-tools.workspace", WORKSPACE::toString);
  }

  @LocalServerPort private int port;
  @Autowired private MockDefinitionRepository definitions;
  private ApiTestClient client;

  @BeforeAll
  void configureRuntimePort() throws Exception {
    writeWorkspace("http://127.0.0.1:" + port);
    definitions.reloadNow();
    client =
        ApiTestClient.builder("http://127.0.0.1:" + port).timeout(Duration.ofSeconds(3)).build();
  }

  @Test
  void servesHttpMockDispatchesCallbackAndExposesAdministrationState() throws Exception {
    var request = client.post("/integration/echo");
    request.query("mode", "full");
    request.header("X-Integration-Test", "true");
    request.jsonBody(Map.of("message", "hello"));

    ApiResponse response = client.send(request);

    assertEquals(201, response.status());
    assertEquals("hello", response.json().path("echo").asText());
    JsonNode callbacks = awaitSuccessfulCallback();
    assertTrue(callbacks.isArray());
    assertFalse(callbacks.isEmpty());
    assertEquals("SUCCESS", callbacks.get(0).path("status").asText());

    JsonNode calls = client.send(client.get("/__testtools/calls")).json();
    assertTrue(calls.isArray());
    assertTrue(calls.toString().contains("integration echo"));

    JsonNode catalog = client.send(client.get("/__testtools/catalog")).json();
    assertEquals(2, catalog.path("mockCount").asInt());
    assertTrue(catalog.path("version").asLong() >= 2);
  }

  private JsonNode awaitSuccessfulCallback() throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
    JsonNode callbacks;
    do {
      callbacks = client.send(client.get("/__testtools/callbacks")).json();
      if (!callbacks.isEmpty() && "SUCCESS".equals(callbacks.get(0).path("status").asText())) {
        return callbacks;
      }
      Thread.sleep(25);
    } while (System.nanoTime() < deadline);
    return callbacks;
  }

  private static Path createWorkspace() {
    try {
      Path workspace = Files.createTempDirectory("test-tools-mock-it-");
      Files.createDirectories(workspace.resolve("environments"));
      Files.createDirectories(workspace.resolve("mocks/cases/integration"));
      writeWorkspace(workspace, "http://127.0.0.1:1");
      Files.writeString(
          workspace.resolve("environments/local.yaml"),
          """
                  name: local
                  baseUrl: http://127.0.0.1
                  """);
      Files.writeString(
          workspace.resolve("mocks/cases/integration/echo.yaml"),
          """
                  name: integration echo
                  request:
                    method: POST
                    path: /integration/echo
                    query:
                      mode: full
                    headers:
                      X-Integration-Test: "true"
                    body:
                      message: hello
                  response:
                    status: 201
                    body:
                      echo: hello
                  afterResponse:
                    - name: integration callback
                      timeoutMs: 1000
                      request:
                        method: POST
                        url: "${callbackBaseUrl}/integration/callback"
                        body:
                          event: COMPLETED
                  """);
      Files.writeString(
          workspace.resolve("mocks/cases/integration/callback.yaml"),
          """
                  name: integration callback receiver
                  request:
                    method: POST
                    path: /integration/callback
                    body:
                      event: COMPLETED
                  response:
                    status: 204
                  """);
      return workspace;
    } catch (IOException error) {
      throw new ExceptionInInitializerError(error);
    }
  }

  private void writeWorkspace(String callbackBaseUrl) throws IOException {
    writeWorkspace(WORKSPACE, callbackBaseUrl);
  }

  private static void writeWorkspace(Path workspace, String callbackBaseUrl) throws IOException {
    Files.writeString(
        workspace.resolve("workspace.yaml"),
        """
                defaultEnvironment: local
                variables:
                  callbackBaseUrl: %s
                """
            .formatted(callbackBaseUrl));
  }
}
