package io.github.localtools.testtools.yamlrunner.model;

import java.util.List;
import java.util.Map;

public record YamlCaseDefinition(String name, String environment, Map<String, String> variables,
                                 List<YamlStepDefinition> steps) {}
