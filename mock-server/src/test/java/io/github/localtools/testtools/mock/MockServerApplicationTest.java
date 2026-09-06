package io.github.localtools.testtools.mock;

import io.github.localtools.testtools.runner.VariableResolver;
import io.github.localtools.testtools.security.DefaultSecurityHandlers;
import io.github.localtools.testtools.workspace.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MockServerApplicationTest {
    @Test
    void servesFileBasedMockAndStructuredNotFoundResponse() throws Exception {
        WorkspaceService workspace = new WorkspaceService(locateWorkspace());
        HttpMockEngine engine = new HttpMockEngine(
                workspace,
                DefaultSecurityHandlers.create(),
                new VariableResolver(workspace.jsonMapper()),
                new MockCallStore()
        );

        var health = engine.execute(new MockHttpServletRequest("GET", "/health"), new byte[0]);
        assertEquals(200, health.response().status());
        assertEquals("UP", workspace.jsonMapper().readTree(health.response().body()).path("status").asText());

        var missing = engine.execute(new MockHttpServletRequest("GET", "/does-not-exist"), new byte[0]);
        assertEquals(404, missing.response().status());
        assertEquals("No mock matched",
                workspace.jsonMapper().readTree(missing.response().body()).path("error").asText());
    }

    private static Path locateWorkspace() {
        Path current = Path.of("").toAbsolutePath();
        Path direct = current.resolve("test-workspace");
        if (Files.exists(direct.resolve("workspace.yaml"))) return direct;
        return current.resolve("../test-workspace").normalize();
    }
}
