package io.github.mengdlmole.testtools.mock.config;

import io.github.mengdlmole.testtools.mock.model.MockDefinition;
import io.github.mengdlmole.testtools.mock.model.MockDefinition.AfterResponse;
import io.github.mengdlmole.testtools.workspace.TestWorkspace;

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
                .map(name -> validate(name, files.readNamedYaml("mocks", name, MockDefinition.class)))
                .sorted(Comparator.comparing(mock -> mock.priority() == null ? 100 : mock.priority()))
                .toList();
    }

    private MockDefinition validate(String fileName, MockDefinition definition) {
        String source = "Mock '" + fileName + "'";
        if (definition == null || !hasText(definition.name())) {
            throw new IllegalArgumentException(source + " name is required");
        }
        if (definition.request() == null) throw new IllegalArgumentException(source + " request is required");
        if (isHttp(definition)) {
            if (!hasText(definition.request().path())) {
                throw new IllegalArgumentException(source + " HTTP request path is required");
            }
            if (definition.response() == null) {
                throw new IllegalArgumentException(source + " response is required");
            }
            rejectBodyConflict(source + " response", definition.response().body(), definition.response().bodyFile());
            if (definition.afterResponse() != null) {
                for (int index = 0; index < definition.afterResponse().size(); index++) {
                    validateCallback(source, definition.afterResponse().get(index), index + 1);
                }
            }
        }
        return definition;
    }

    private void validateCallback(String source, AfterResponse callback, int index) {
        String callbackSource = source + " afterResponse[" + index + "]";
        if (callback == null) throw new IllegalArgumentException(callbackSource + " must not be null");
        if (callback.request() == null) throw new IllegalArgumentException(callbackSource + " request is required");
        if (!hasText(callback.request().url())) throw new IllegalArgumentException(callbackSource + " URL is required");
        rejectBodyConflict(callbackSource + " request", callback.request().body(), callback.request().bodyFile());
    }

    private void rejectBodyConflict(String source, Object body, String bodyFile) {
        if (bodyFile != null && bodyFile.isBlank()) {
            throw new IllegalArgumentException(source + " bodyFile must not be blank");
        }
        if (body != null && hasText(bodyFile)) {
            throw new IllegalArgumentException(source + " may use body or bodyFile, not both");
        }
    }

    private boolean isHttp(MockDefinition definition) {
        return definition.protocol() == null || definition.protocol().isBlank()
                || "http".equalsIgnoreCase(definition.protocol());
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
