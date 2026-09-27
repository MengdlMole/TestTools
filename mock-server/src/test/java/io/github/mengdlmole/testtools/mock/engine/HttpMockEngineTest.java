package io.github.mengdlmole.testtools.mock.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.mengdlmole.testtools.mock.admin.MockCallStore;
import io.github.mengdlmole.testtools.mock.config.MockWorkspace;
import io.github.mengdlmole.testtools.security.SecurityHandlerLoader;
import io.github.mengdlmole.testtools.workspace.TestWorkspace;
import io.github.mengdlmole.testtools.workspace.VariableResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;

class HttpMockEngineTest {
  @TempDir Path temporary;

  @Test
  void servesFileBasedMockAndStructuredNotFoundResponse() throws Exception {
    TestWorkspace workspace = new TestWorkspace(locateWorkspace());
    HttpMockEngine engine = engine(workspace);

    var health = engine.execute(new MockHttpServletRequest("GET", "/health"), new byte[0]);
    assertEquals(200, health.response().status());
    assertEquals(
        "UP", workspace.jsonMapper().readTree(health.response().body()).path("status").asText());

    var missing = engine.execute(new MockHttpServletRequest("GET", "/does-not-exist"), new byte[0]);
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

    var explicit = engine.execute(new MockHttpServletRequest("GET", "/explicit-text"), new byte[0]);
    assertEquals("text/plain", explicit.response().headers().get("content-type"));
    assertFalse(explicit.response().headers().containsKey("Content-Type"));

    var bodyFile = engine.execute(new MockHttpServletRequest("GET", "/body-file"), new byte[0]);
    assertEquals("plain response", bodyFile.response().bodyText());
    assertFalse(
        bodyFile.response().headers().keySet().stream().anyMatch("Content-Type"::equalsIgnoreCase));
  }

  private HttpMockEngine engine(TestWorkspace workspace) {
    return new HttpMockEngine(
        new MockWorkspace(workspace),
        SecurityHandlerLoader.create(),
        new VariableResolver(workspace.jsonMapper()),
        new MockCallStore());
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
