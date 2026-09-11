package io.github.mengdlmole.testtools.yamlrunner;

import io.github.mengdlmole.testtools.workspace.TestWorkspace;
import io.github.mengdlmole.testtools.yamlrunner.model.YamlCaseDefinition;
import io.github.mengdlmole.testtools.yamlrunner.model.YamlRunnerConfig;
import io.github.mengdlmole.testtools.yamlrunner.model.YamlStepDefinition;
import io.github.mengdlmole.testtools.yamlrunner.model.YamlSuiteDefinition;

import java.util.List;

/** YAML-runner-specific view of the shared test workspace. */
public final class YamlWorkspace {
    private final TestWorkspace files;

    public YamlWorkspace(TestWorkspace files) { this.files = files; }

    public TestWorkspace files() { return files; }
    public List<String> caseNames() { return files.listYamlNames("cases"); }
    public List<String> suiteNames() { return files.listYamlNames("suites"); }
    public YamlCaseDefinition testCase(String name) {
        return validateCase(files.readNamedYaml("cases", name, YamlCaseDefinition.class));
    }
    public YamlSuiteDefinition suite(String name) {
        return validateSuite(files.readNamedYaml("suites", name, YamlSuiteDefinition.class));
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
        YamlCaseDefinition parsed = validateCase(files.parseYaml(yaml, YamlCaseDefinition.class));
        files.writeNamedYamlText("cases", name, yaml);
        return parsed;
    }

    public synchronized YamlSuiteDefinition saveSuiteYaml(String name, String yaml) {
        if (yaml == null || yaml.isBlank()) throw new IllegalArgumentException("Suite YAML must not be empty");
        YamlSuiteDefinition parsed = validateSuite(files.parseYaml(yaml, YamlSuiteDefinition.class));
        files.writeNamedYamlText("suites", name, yaml);
        return parsed;
    }

    private YamlCaseDefinition validateCase(YamlCaseDefinition definition) {
        if (definition == null || !hasText(definition.name())) {
            throw new IllegalArgumentException("Case name is required");
        }
        if (definition.steps() == null || definition.steps().isEmpty()) {
            throw new IllegalArgumentException("At least one case step is required");
        }
        for (int index = 0; index < definition.steps().size(); index++) {
            validateStep(definition.steps().get(index), index + 1);
        }
        return definition;
    }

    private void validateStep(YamlStepDefinition step, int index) {
        if (step == null) throw new IllegalArgumentException("Case step " + index + " must not be null");
        if (!hasText(step.name())) throw new IllegalArgumentException("Case step " + index + " name is required");
        if (!hasText(step.path())) throw new IllegalArgumentException("Case step '" + step.name() + "' path is required");
        rejectBlank("bodyFile", step.bodyFile(), step.name());
        rejectBlank("globalBodyFile", step.globalBodyFile(), step.name());
        rejectBlank("caseBodyFile", step.caseBodyFile(), step.name());
        int bodySources = (step.body() == null ? 0 : 1)
                + (hasText(step.bodyFile()) ? 1 : 0)
                + (hasText(step.globalBodyFile()) ? 1 : 0)
                + (hasText(step.caseBodyFile()) ? 1 : 0);
        if (bodySources > 1) {
            throw new IllegalArgumentException("Case step '" + step.name()
                    + "' may use only one body source");
        }
    }

    private YamlSuiteDefinition validateSuite(YamlSuiteDefinition definition) {
        if (definition == null || !hasText(definition.name())) {
            throw new IllegalArgumentException("Suite name is required");
        }
        if (definition.cases() == null || definition.cases().isEmpty()) {
            throw new IllegalArgumentException("At least one suite case is required");
        }
        if (definition.cases().stream().anyMatch(name -> !hasText(name))) {
            throw new IllegalArgumentException("Suite case name must not be blank");
        }
        return definition;
    }

    private void rejectBlank(String field, String value, String stepName) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException("Case step '" + stepName + "' " + field + " must not be blank");
        }
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
