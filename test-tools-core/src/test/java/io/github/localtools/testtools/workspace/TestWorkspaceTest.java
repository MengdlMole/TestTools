package io.github.localtools.testtools.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestWorkspaceTest {
    @TempDir Path root;

    @Test
    void readsNamedYmlFilesFromAnyFeatureDirectory() throws Exception {
        Files.createDirectories(root.resolve("feature"));
        Files.writeString(root.resolve("feature/example.yml"), "name: example\n");
        TestWorkspace workspace = new TestWorkspace(root);

        assertEquals("example", workspace.listYamlNames("feature").getFirst());
        assertEquals("example", workspace.readNamedYaml("feature", "example", NamedConfig.class).name());
    }

    @Test
    void rejectsUnknownYamlFieldsSoTyposDoNotSilentlyPass() throws Exception {
        Files.writeString(root.resolve("workspace.yaml"), "defaultEnvironment: local\nunknownField: typo\n");
        assertThrows(WorkspaceException.class, () -> new TestWorkspace(root).config());
    }

    @Test
    void resolvesGlobalAndCaseOwnedJsonFixturesAndRejectsTraversal() throws Exception {
        Files.createDirectories(root.resolve("fixtures/global"));
        Files.createDirectories(root.resolve("fixtures/cases/create-order"));
        Files.writeString(root.resolve("fixtures/global/common.json"), "{\"scope\":\"global\"}");
        Files.writeString(root.resolve("fixtures/cases/create-order/request.json"), "{\"scope\":\"case\"}");
        TestWorkspace workspace = new TestWorkspace(root);

        assertEquals("global", workspace.readJson(workspace.globalJsonFile("common.json")).path("scope").asText());
        assertEquals("case", workspace.readJson(
                workspace.caseJsonFile("create-order", "request.json")).path("scope").asText());
        assertThrows(IllegalArgumentException.class, () -> workspace.globalJsonFile("../secret.json"));
        assertThrows(IllegalArgumentException.class, () -> workspace.caseJsonFile("create-order", "../other.json"));
    }

    @Test
    void rejectsJsonFixtureDirectorySymlinkedOutsideWorkspace(@TempDir Path outside) throws Exception {
        Files.writeString(outside.resolve("request.json"), "{}");
        Files.createDirectories(root.resolve("fixtures"));
        Files.createSymbolicLink(root.resolve("fixtures/global"), outside);

        assertThrows(IllegalArgumentException.class,
                () -> new TestWorkspace(root).globalJsonFile("request.json"));
    }

    private record NamedConfig(String name) {}
}
