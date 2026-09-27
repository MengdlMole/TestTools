package io.github.mengdlmole.testtools.mock.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.mengdlmole.testtools.http.transport.RequestSnapshot;
import io.github.mengdlmole.testtools.mock.config.MockDefinitionRepository;
import io.github.mengdlmole.testtools.mock.config.MockWorkspace;
import io.github.mengdlmole.testtools.security.SecurityHandlerLoader;
import io.github.mengdlmole.testtools.workspace.TestWorkspace;
import io.github.mengdlmole.testtools.workspace.VariableResolver;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HttpMockEngineTest {
  @TempDir Path temporary;

  @Test
  void servesFileBasedMockAndStructuredNotFoundResponse() throws Exception {
    TestWorkspace workspace = new TestWorkspace(locateWorkspace());
    HttpMockEngine engine = engine(workspace);

    var health = engine.execute(request("GET", "/health"));
    assertEquals(200, health.response().status());
    assertEquals(
        "UP", workspace.jsonMapper().readTree(health.response().body()).path("status").asText());

    var missing = engine.execute(request("GET", "/does-not-exist"));
    assertEquals(404, missing.response().status());
    assertEquals(
        "No mock matched",
        workspace.jsonMapper().readTree(missing.response().body()).path("error").asText());
  }

  @Test
  void respectsCaseInsensitiveContentTypeAndDoesNotLabelBodyFilesAsJson() throws Exception {
    Files.createDirectories(temporary.resolve("environments"));
    Files.createDirectories(temporary.resolve("mocks/cases/content"));
    Files.createDirectories(temporary.resolve("fixtures"));
    Files.writeString(temporary.resolve("workspace.yaml"), "defaultEnvironment: local\n");
    Files.writeString(
        temporary.resolve("environments/local.yaml"),
        """
                name: local
                baseUrl: http://127.0.0.1
                """);
    Files.writeString(temporary.resolve("fixtures/plain.txt"), "plain response");
    Files.writeString(
        temporary.resolve("mocks/cases/content/lowercase-content-type.yaml"),
        """
                name: explicit text
                priority: 1
                request:
                  method: GET
                  path: /explicit-text
                response:
                  headers:
                    content-type: text/plain
                  body:
                    value: text
                """);
    Files.writeString(
        temporary.resolve("mocks/cases/content/body-file.yaml"),
        """
                name: body file
                priority: 2
                request:
                  method: GET
                  path: /body-file
                response:
                  bodyFile: fixtures/plain.txt
                """);
    TestWorkspace workspace = new TestWorkspace(temporary);
    HttpMockEngine engine = engine(workspace);

    var explicit = engine.execute(request("GET", "/explicit-text"));
    assertEquals("text/plain", explicit.response().headers().get("content-type"));
    assertFalse(explicit.response().headers().containsKey("Content-Type"));

    var bodyFile = engine.execute(request("GET", "/body-file"));
    assertEquals("plain response", bodyFile.response().bodyText());
    assertFalse(
        bodyFile.response().headers().keySet().stream().anyMatch("Content-Type"::equalsIgnoreCase));
  }

  private HttpMockEngine engine(TestWorkspace workspace) {
    MockWorkspace mocks = new MockWorkspace(workspace);
    return new HttpMockEngine(
        new MockDefinitionRepository(mocks, 500),
        SecurityHandlerLoader.create(),
        new VariableResolver(workspace.jsonMapper()),
        workspace.jsonMapper());
  }

  private MockRequest request(String method, String path) {
    return new MockRequest(
        new RequestSnapshot(method, URI.create(path), Map.of(), new byte[0]), Map.of());
  }

  private static Path locateWorkspace() {
    Path current = Path.of("").toAbsolutePath();
    Path direct = current.resolve("test-workspace");
    if (Files.exists(direct.resolve("workspace.yaml"))) {
      return direct;
    }
    return current.resolve("../test-workspace").normalize();
  }
}
