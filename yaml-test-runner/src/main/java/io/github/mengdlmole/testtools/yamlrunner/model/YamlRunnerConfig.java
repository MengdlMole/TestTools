package io.github.mengdlmole.testtools.yamlrunner.model;

import java.util.List;

public record YamlRunnerConfig(List<SecurityRule> securityRules) {
    public record SecurityRule(String operationId, String method, String pathPattern, String handler) {}
}
