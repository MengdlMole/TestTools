package io.github.localtools.testtools.workspace;

import java.nio.file.Files;
import java.nio.file.Path;

/** Locates the shared test workspace consistently from IDEs, Maven and executable jars. */
public final class WorkspaceLocator {
    private WorkspaceLocator() {}

    public static Path locate(String explicitPath) {
        String configured = hasText(explicitPath) ? explicitPath : System.getProperty("testtools.workspace");
        if (hasText(configured)) return Path.of(configured.trim()).toAbsolutePath().normalize();

        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            Path workspace = candidate.resolve("test-workspace");
            if (Files.isRegularFile(workspace.resolve("workspace.yaml"))) return workspace;
        }
        return current.resolve("test-workspace");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
