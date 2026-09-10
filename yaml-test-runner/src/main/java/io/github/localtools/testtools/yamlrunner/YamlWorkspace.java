package io.github.localtools.testtools.yamlrunner;

import io.github.localtools.testtools.workspace.TestWorkspace;
import io.github.localtools.testtools.yamlrunner.model.YamlCaseDefinition;
import io.github.localtools.testtools.yamlrunner.model.YamlRunnerConfig;
import io.github.localtools.testtools.yamlrunner.model.YamlSuiteDefinition;

import java.util.List;

/** YAML-runner-specific view of the shared test workspace. */
public final class YamlWorkspace {
    private final TestWorkspace files;

    public YamlWorkspace(TestWorkspace files) { this.files = files; }

    public TestWorkspace files() { return files; }
    public List<String> caseNames() { return files.listYamlNames("cases"); }
    public List<String> suiteNames() { return files.listYamlNames("suites"); }
    public YamlCaseDefinition testCase(String name) {
        return files.readNamedYaml("cases", name, YamlCaseDefinition.class);
    }
    public YamlSuiteDefinition suite(String name) {
        return files.readNamedYaml("suites", name, YamlSuiteDefinition.class);
    }
    public YamlRunnerConfig config() {
        return java.nio.file.Files.isRegularFile(files.root().resolve("yaml-runner.yaml"))
                ? files.readYaml("yaml-runner.yaml", YamlRunnerConfig.class)
                : new YamlRunnerConfig(List.of());
    }
    public String caseYaml(String name) { return files.readNamedYamlText("cases", name); }
    public String suiteYaml(String name) { return files.readNamedYamlText("suites", name); }

    public synchronized YamlCaseDefinition saveCaseYaml(String name, String yaml) {
        if (yaml == null || yaml.isBlank()) throw new IllegalArgumentException("Case YAML must not be empty");
        YamlCaseDefinition parsed = files.parseYaml(yaml, YamlCaseDefinition.class);
        if (parsed.name() == null || parsed.name().isBlank()) throw new IllegalArgumentException("Case name is required");
        if (parsed.steps() == null || parsed.steps().isEmpty()) throw new IllegalArgumentException("At least one step is required");
        files.writeNamedYamlText("cases", name, yaml);
        return parsed;
    }

    public synchronized YamlSuiteDefinition saveSuiteYaml(String name, String yaml) {
        if (yaml == null || yaml.isBlank()) throw new IllegalArgumentException("Suite YAML must not be empty");
        YamlSuiteDefinition parsed = files.parseYaml(yaml, YamlSuiteDefinition.class);
        if (parsed.name() == null || parsed.name().isBlank()) throw new IllegalArgumentException("Suite name is required");
        if (parsed.cases() == null || parsed.cases().isEmpty()) throw new IllegalArgumentException("At least one case is required");
        files.writeNamedYamlText("suites", name, yaml);
        return parsed;
    }
}
