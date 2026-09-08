package io.github.localtools.testtools.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkspaceServiceTest {
    @TempDir Path root;

    @Test
    void readsYmlFilesDiscoveredByTheWorkspaceScanner() throws Exception {
        Files.createDirectories(root.resolve("cases"));
        Files.writeString(root.resolve("cases/example.yml"), """
                name: yml case
                steps:
                  - name: health
                    method: GET
                    path: /health
                """);

        WorkspaceService workspace = new WorkspaceService(root);

        assertEquals("example", workspace.caseNames().getFirst());
        assertEquals("yml case", workspace.testCase("example").name());
    }

    @Test
    void rejectsUnknownYamlFieldsSoTyposDoNotSilentlyPass() throws Exception {
        Files.writeString(root.resolve("workspace.yaml"), """
                defaultEnvironment: local
                unknowField: typo
                """);

        assertThrows(WorkspaceException.class, () -> new WorkspaceService(root).config());
    }
}
