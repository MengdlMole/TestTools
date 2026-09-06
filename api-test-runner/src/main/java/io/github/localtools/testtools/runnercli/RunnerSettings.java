package io.github.localtools.testtools.runnercli;

import java.nio.file.Path;

final class RunnerSettings {
    static Path workspace = Path.of("test-workspace");
    static String caseName;
    static String suiteName;

    private RunnerSettings() {}
}
