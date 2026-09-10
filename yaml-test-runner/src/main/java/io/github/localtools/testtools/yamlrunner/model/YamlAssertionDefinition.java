package io.github.localtools.testtools.yamlrunner.model;

import com.fasterxml.jackson.databind.JsonNode;

public record YamlAssertionDefinition(String type, String path, String operator, JsonNode expected) {}
