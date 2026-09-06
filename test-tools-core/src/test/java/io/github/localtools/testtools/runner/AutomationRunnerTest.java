package io.github.localtools.testtools.runner;

import io.github.localtools.testtools.TestToolsRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomationRunnerTest {
    @TempDir Path workspace;

    @Test
    void persistsConfigurationErrorsAsFailedCaseResults() throws Exception {
        Files.createDirectories(workspace.resolve("cases"));
        Files.writeString(workspace.resolve("workspace.yaml"), "defaultEnvironment: missing\n");
        Files.writeString(workspace.resolve("cases/broken.yaml"), "name: broken\nsteps:\n  - name: never runs\n    path: /\n");

        var execution = TestToolsRuntime.open(workspace).automationRunner().runCase("broken");

        assertFalse(execution.result().success());
        assertNotNull(execution.result().error());
        assertTrue(Files.exists(workspace.resolve("results").resolve(execution.resultFile())));
    }
}
