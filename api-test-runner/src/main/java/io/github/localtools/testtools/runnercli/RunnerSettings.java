package io.github.localtools.testtools.runnercli;

import java.nio.file.Files;
import java.nio.file.Path;

final class RunnerSettings {
    static Path workspace = workspaceFromSystemProperty();
    static String caseName = textProperty("testtools.case");
    static String suiteName = textProperty("testtools.suite");

    private RunnerSettings() {}

    static void validateSelection() {
        if (caseName != null && suiteName != null) {
            throw new IllegalArgumentException("Use either a case or a suite, not both");
        }
    }

    private static Path workspaceFromSystemProperty() {
        String configured = textProperty("testtools.workspace");
        if (configured != null) return Path.of(configured).toAbsolutePath().normalize();

        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            Path workspace = candidate.resolve("test-workspace");
            if (Files.isRegularFile(workspace.resolve("workspace.yaml"))) return workspace;
        }
        return current.resolve("test-workspace");
    }

    private static String textProperty(String name) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
