package io.github.localtools.testtools.yamlrunner;

import org.testng.TestNG;

import java.nio.file.Path;

public final class YamlTestMain {
    private YamlTestMain() {}

    public static void main(String[] args) {
        parse(args);
        TestNG testng = new TestNG();
        testng.setTestClasses(new Class<?>[]{YamlSuiteTest.class});
        testng.setDefaultSuiteName("Local API Tests");
        testng.setDefaultTestName(selectionName());
        testng.setOutputDirectory(YamlRunSelection.workspace.resolve("results/testng").toString());
        testng.setUseDefaultListeners(true);
        testng.run();
        if (testng.hasFailure()) System.exit(1);
    }

    private static void parse(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--workspace" -> YamlRunSelection.workspace = Path.of(value(args, ++i, "--workspace"));
                case "--case" -> YamlRunSelection.caseName = value(args, ++i, "--case");
                case "--suite" -> YamlRunSelection.suiteName = value(args, ++i, "--suite");
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        YamlRunSelection.validateSelection();
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) throw new IllegalArgumentException("Missing value for " + option);
        return args[index];
    }

    private static String selectionName() {
        if (YamlRunSelection.caseName != null) return "case: " + YamlRunSelection.caseName;
        if (YamlRunSelection.suiteName != null) return "suite: " + YamlRunSelection.suiteName;
        return "all cases";
    }
}
