package io.github.localtools.testtools.yamlrunner.engine;

import io.github.localtools.testtools.workspace.TestWorkspace;
import io.github.localtools.testtools.yamlrunner.YamlWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlSuiteRunnerTest {
    @TempDir Path workspace;

    @Test
    void persistsConfigurationErrorsAsFailedCaseResults() throws Exception {
        Files.createDirectories(workspace.resolve("cases"));
        Files.writeString(workspace.resolve("workspace.yaml"), "defaultEnvironment: missing\n");
        Files.writeString(workspace.resolve("cases/broken.yaml"), "name: broken\nsteps:\n  - name: never runs\n    path: /\n");

        var execution = YamlTestRuntime.open(workspace).suiteRunner().runCase("broken");

        assertFalse(execution.result().success());
        assertNotNull(execution.result().error());
        assertTrue(Files.exists(workspace.resolve("results").resolve(execution.resultFile())));
    }

    @Test
    void rejectsEmptySuiteLoadedDirectlyFromFile() throws Exception {
        Files.createDirectories(workspace.resolve("suites"));
        Files.writeString(workspace.resolve("suites/empty.yaml"), "name: empty\ncases: []\n");

        YamlWorkspace yamlWorkspace = new YamlWorkspace(new TestWorkspace(workspace));

        assertThrows(IllegalArgumentException.class, () -> yamlWorkspace.suite("empty"));
    }
}
