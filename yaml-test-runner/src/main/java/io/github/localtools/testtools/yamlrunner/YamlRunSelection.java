package io.github.localtools.testtools.yamlrunner;

import java.nio.file.Path;
import io.github.localtools.testtools.workspace.WorkspaceLocator;

final class YamlRunSelection {
    static Path workspace = WorkspaceLocator.locate(null);
    static String caseName = textProperty("testtools.case");
    static String suiteName = textProperty("testtools.suite");

    private YamlRunSelection() {}

    static void validateSelection() {
        if (caseName != null && suiteName != null) {
            throw new IllegalArgumentException("Use either a case or a suite, not both");
        }
    }

    private static String textProperty(String name) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
