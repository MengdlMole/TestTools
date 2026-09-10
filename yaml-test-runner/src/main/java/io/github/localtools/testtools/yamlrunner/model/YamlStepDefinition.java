package io.github.localtools.testtools.yamlrunner.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public record YamlStepDefinition(String name, String operationId, String protocol, String method, String path,
                                 Map<String, String> query, Map<String, String> headers,
                                 String bodyFile, String globalBodyFile, String caseBodyFile,
                                 JsonNode body, String securityHandler,
                                 Map<String, String> extract, List<YamlAssertionDefinition> assertions) {}
