package io.github.localtools.testtools.runner;

import io.github.localtools.testtools.http.HttpExecutor;
import io.github.localtools.testtools.http.HttpModels.MutableRequest;
import io.github.localtools.testtools.http.HttpModels.RequestSnapshot;
import io.github.localtools.testtools.http.HttpModels.ResponseSnapshot;
import io.github.localtools.testtools.security.NoSecurityHandler;
import io.github.localtools.testtools.security.SecurityHandlerRegistry;
import io.github.localtools.testtools.workspace.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCaseRunnerJsonFixtureTest {
    @TempDir Path root;
    private WorkspaceService workspace;
    private CapturingExecutor executor;
    private TestCaseRunner runner;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(root.resolve("environments"));
        Files.createDirectories(root.resolve("cases"));
        Files.createDirectories(root.resolve("fixtures/global"));
        Files.writeString(root.resolve("workspace.yaml"), "defaultEnvironment: local\n");
        Files.writeString(root.resolve("environments/local.yaml"), """
                name: local
                baseUrl: http://localhost
                variables:
                  tenantId: T1001
                """);
        workspace = new WorkspaceService(root);
        executor = new CapturingExecutor();
        runner = new TestCaseRunner(workspace, new VariableResolver(workspace.jsonMapper()),
                new SecurityHandlerRegistry(List.of(new NoSecurityHandler())), executor,
                new AssertionEngine(workspace.jsonMapper()));
    }

    @Test
    void loadsGlobalAndCaseJsonAndRejectsConflictingOrBlankSources() throws Exception {
        Files.writeString(root.resolve("fixtures/global/request.json"),
                "{\"scope\":\"global\",\"tenantId\":\"${tenantId}\"}");
        writeCase("global-json", "globalBodyFile: request.json");

        Files.createDirectories(root.resolve("cases/case-json/fixtures"));
        Files.writeString(root.resolve("cases/case-json/fixtures/request.json"), "{\"scope\":\"case\"}");
        writeCase("case-json", "caseBodyFile: request.json");

        writeCase("conflict", "globalBodyFile: request.json\n    body: {scope: inline}");
        writeCase("blank", "globalBodyFile: \"\"");

        assertTrue(runner.run("global-json").success());
        assertEquals("T1001", workspace.jsonMapper().readTree(executor.bodies.get(0)).path("tenantId").asText());
        assertTrue(runner.run("case-json").success());
        assertEquals("case", workspace.jsonMapper().readTree(executor.bodies.get(1)).path("scope").asText());
        assertFalse(runner.run("conflict").success());
        assertFalse(runner.run("blank").success());
        assertEquals(2, executor.bodies.size());
    }

    private void writeCase(String name, String bodySource) throws Exception {
        Files.writeString(root.resolve("cases").resolve(name + ".yaml"), """
                name: fixture test
                environment: local
                steps:
                  - name: send
                    method: POST
                    path: /echo
                    %s
                """.formatted(bodySource));
    }

    private static final class CapturingExecutor extends HttpExecutor {
        private final List<byte[]> bodies = new ArrayList<>();

        @Override
        public Exchange execute(MutableRequest request, Duration timeout) {
            bodies.add(request.body().clone());
            RequestSnapshot sent = new RequestSnapshot(request.method(), request.uri(),
                    Map.of(), request.body().clone());
            ResponseSnapshot response = new ResponseSnapshot(200, Map.of(),
                    "{}".getBytes(StandardCharsets.UTF_8), 1);
            return new Exchange(sent, response);
        }
    }
}
