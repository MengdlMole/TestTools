package io.github.localtools.testtools.runnercli;

import org.testng.TestNG;

import java.nio.file.Path;

public final class ApiTestMain {
    private ApiTestMain() {}

    public static void main(String[] args) {
        parse(args);
        TestNG testng = new TestNG();
        testng.setTestClasses(new Class<?>[]{FileDrivenApiTest.class});
        testng.setDefaultSuiteName("Local API Tests");
        testng.setDefaultTestName(selectionName());
        testng.setOutputDirectory(RunnerSettings.workspace.resolve("results/testng").toString());
        testng.setUseDefaultListeners(true);
        testng.run();
        if (testng.hasFailure()) System.exit(1);
    }

    private static void parse(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--workspace" -> RunnerSettings.workspace = Path.of(value(args, ++i, "--workspace"));
                case "--case" -> RunnerSettings.caseName = value(args, ++i, "--case");
                case "--suite" -> RunnerSettings.suiteName = value(args, ++i, "--suite");
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        if (RunnerSettings.caseName != null && RunnerSettings.suiteName != null) {
            throw new IllegalArgumentException("Use either --case or --suite, not both");
        }
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) throw new IllegalArgumentException("Missing value for " + option);
        return args[index];
    }

    private static String selectionName() {
        if (RunnerSettings.caseName != null) return "case: " + RunnerSettings.caseName;
        if (RunnerSettings.suiteName != null) return "suite: " + RunnerSettings.suiteName;
        return "all cases";
    }
}
