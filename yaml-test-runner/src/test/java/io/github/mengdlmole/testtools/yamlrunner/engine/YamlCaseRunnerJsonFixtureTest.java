package io.github.mengdlmole.testtools.yamlrunner.engine;

import io.github.mengdlmole.testtools.http.HttpExecutor;
import io.github.mengdlmole.testtools.http.MutableRequest;
import io.github.mengdlmole.testtools.http.RequestSnapshot;
import io.github.mengdlmole.testtools.http.ResponseSnapshot;
import io.github.mengdlmole.testtools.workspace.VariableResolver;
import io.github.mengdlmole.testtools.security.NoSecurityHandler;
import io.github.mengdlmole.testtools.security.SecurityHandlerRegistry;
import io.github.mengdlmole.testtools.workspace.TestWorkspace;
import io.github.mengdlmole.testtools.yamlrunner.YamlSecurityResolver;
import io.github.mengdlmole.testtools.yamlrunner.YamlWorkspace;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlCaseRunnerJsonFixtureTest {
    @TempDir Path root;
    private TestWorkspace workspace;
    private CapturingExecutor executor;
    private YamlCaseRunner runner;

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
        workspace = new TestWorkspace(root);
        executor = new CapturingExecutor();
        SecurityHandlerRegistry handlers = new SecurityHandlerRegistry(List.of(new NoSecurityHandler()));
        runner = new YamlCaseRunner(new YamlWorkspace(workspace), new VariableResolver(workspace.jsonMapper()),
                new YamlSecurityResolver(handlers, List.of()), executor,
                new YamlAssertionEngine(workspace.jsonMapper()));
    }

    @Test
    void loadsGlobalAndCaseJsonAndRejectsConflictingOrBlankSources() throws Exception {
        Files.writeString(root.resolve("fixtures/global/request.json"),
                "{\"scope\":\"global\",\"tenantId\":\"${tenantId}\"}");
        writeCase("global-json", "globalBodyFile: request.json");

        Files.createDirectories(root.resolve("fixtures/cases/case-json"));
        Files.writeString(root.resolve("fixtures/cases/case-json/request.json"), "{\"scope\":\"case\"}");
        writeCase("case-json", "caseBodyFile: request.json");

        writeCase("conflict", "globalBodyFile: request.json\n    body: {scope: inline}");
        writeCase("blank", "globalBodyFile: \"\"");

        assertTrue(runner.run("global-json").success());
        assertEquals("T1001", workspace.jsonMapper().readTree(executor.bodies.get(0)).path("tenantId").asText());
        assertTrue(runner.run("case-json").success());
        assertEquals("case", workspace.jsonMapper().readTree(executor.bodies.get(1)).path("scope").asText());
        assertThrows(IllegalArgumentException.class, () -> runner.run("conflict"));
        assertThrows(IllegalArgumentException.class, () -> runner.run("blank"));
        assertEquals(2, executor.bodies.size());
    }

    @Test
    void rawBodyFileKeepsExplicitMediaTypeAndUsesSharedUriAssembly() throws Exception {
        Files.createDirectories(root.resolve("fixtures/raw"));
        Files.writeString(root.resolve("fixtures/raw/message.txt"), "hello ${tenantId}");
        writeCase("raw-body", """
                path: /echo?existing=true#response
                    query:
                      name: hello world
                    headers:
                      Content-Type: text/plain
                    bodyFile: fixtures/raw/message.txt
                """);

        assertTrue(runner.run("raw-body").success());
        assertEquals("hello T1001", new String(executor.bodies.getFirst(), StandardCharsets.UTF_8));
        assertEquals("text/plain", executor.headers.getFirst().get("Content-Type"));
        assertEquals("http://localhost/echo?existing=true&name=hello+world#response",
                executor.uris.getFirst());
    }

    private void writeCase(String name, String bodySource) throws Exception {
        Files.writeString(root.resolve("cases").resolve(name + ".yaml"), """
                name: fixture test
                environment: local
                steps:
                  - name: send
                    method: POST
                    %s
                    %s
                """.formatted(bodySource.contains("path:") ? "" : "path: /echo", bodySource));
    }

    private static final class CapturingExecutor extends HttpExecutor {
        private final List<byte[]> bodies = new ArrayList<>();
        private final List<Map<String, String>> headers = new ArrayList<>();
        private final List<String> uris = new ArrayList<>();

        @Override
        public Exchange execute(MutableRequest request, Duration timeout) {
            bodies.add(request.body().clone());
            headers.add(Map.copyOf(request.headers()));
            uris.add(request.uri().toString());
            RequestSnapshot sent = new RequestSnapshot(request.method(), request.uri(),
                    Map.of(), request.body().clone());
            ResponseSnapshot response = new ResponseSnapshot(200, Map.of(),
                    "{}".getBytes(StandardCharsets.UTF_8), 1);
            return new Exchange(sent, response);
        }
    }
}
