package io.github.localtools.testtools.mock.config;

import io.github.localtools.testtools.mock.model.MockDefinition;
import io.github.localtools.testtools.workspace.TestWorkspace;

import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;

/** Mock-server-specific view of the shared test workspace. */
public final class MockWorkspace {
    private final TestWorkspace files;

    public MockWorkspace(TestWorkspace files) { this.files = files; }

    public TestWorkspace files() { return files; }

    public MockServerConfig config() {
        return Files.isRegularFile(files.root().resolve("mock-server.yaml"))
                ? files.readYaml("mock-server.yaml", MockServerConfig.class)
                : new MockServerConfig(19090);
    }

    public List<MockDefinition> definitions() {
        return files.listYamlNames("mocks").stream()
                .map(name -> files.readNamedYaml("mocks", name, MockDefinition.class))
                .sorted(Comparator.comparing(mock -> mock.priority() == null ? 100 : mock.priority()))
                .toList();
    }
}
