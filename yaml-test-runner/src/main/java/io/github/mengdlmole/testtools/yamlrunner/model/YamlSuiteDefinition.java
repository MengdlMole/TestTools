package io.github.mengdlmole.testtools.yamlrunner.model;

import java.util.List;

public record YamlSuiteDefinition(String name, List<String> cases, Boolean stopOnFailure, Integer repeat) {}
