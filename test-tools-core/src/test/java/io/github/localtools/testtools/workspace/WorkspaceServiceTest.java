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

    @Test
    void resolvesGlobalAndCaseOwnedJsonFixturesAndRejectsTraversal() throws Exception {
        Files.createDirectories(root.resolve("fixtures/global"));
        Files.createDirectories(root.resolve("cases/create-order/fixtures"));
        Files.writeString(root.resolve("fixtures/global/common.json"), "{\"scope\":\"global\"}");
        Files.writeString(root.resolve("cases/create-order/fixtures/request.json"), "{\"scope\":\"case\"}");
        WorkspaceService workspace = new WorkspaceService(root);

        assertEquals("global", workspace.jsonFile(workspace.globalJsonFile("common.json")).path("scope").asText());
        assertEquals("case", workspace.jsonFile(
                workspace.caseJsonFile("create-order", "request.json")).path("scope").asText());
        assertThrows(IllegalArgumentException.class, () -> workspace.globalJsonFile("../secret.json"));
        assertThrows(IllegalArgumentException.class, () -> workspace.caseJsonFile("create-order", "../other.json"));
    }

    @Test
    void rejectsJsonFixtureDirectorySymlinkedOutsideWorkspace(@TempDir Path outside) throws Exception {
        Files.writeString(outside.resolve("request.json"), "{}");
        Files.createDirectories(root.resolve("fixtures"));
        Files.createSymbolicLink(root.resolve("fixtures/global"), outside);

        WorkspaceService workspace = new WorkspaceService(root);

        assertThrows(IllegalArgumentException.class, () -> workspace.globalJsonFile("request.json"));
    }
}
