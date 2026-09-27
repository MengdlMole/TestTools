package io.github.mengdlmole.testtools.apitest.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mengdlmole.testtools.http.ApiTestClient;
import io.github.mengdlmole.testtools.workspace.EnvironmentConfig;
import io.github.mengdlmole.testtools.workspace.EnvironmentContext;
import io.github.mengdlmole.testtools.workspace.TestWorkspace;
import io.github.mengdlmole.testtools.workspace.VariableResolver;
import io.github.mengdlmole.testtools.workspace.WorkspaceLocator;
import java.nio.file.Path;
import java.util.Map;

/**
 * Common setup helpers for code-first JUnit API test classes.
 */
public abstract class ApiTestSupport {
  private TestWorkspace workspace;

  protected final ApiTestClient api(String environmentName) {
    EnvironmentConfig environment = workspace().environment(environmentName);
    return ApiTestClient.builder(environment.baseUrl())
        .objectMapper(workspace().jsonMapper())
        .build();
  }

  protected final String secret(String environmentName, String name) {
    String value = workspace().environmentContext(environmentName).secrets().get(name);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing secret: " + name);
    }
    return value;
  }

  /**
   * Shared JSON body stored in test-workspace/fixtures/api-tests/global.
   *
   * @param relativePath path relative to the global fixture directory
   * @return resolved fixture path
   */
  protected final Path globalFile(String relativePath) {
    return workspace().apiGlobalJsonFile(relativePath);
  }

  /**
   * Case-owned JSON body stored in test-workspace/fixtures/api-tests/cases/{caseName}.
   *
   * @param caseName case directory name
   * @param relativePath path relative to the case fixture directory
   * @return resolved fixture path
   */
  protected final Path caseFile(String caseName, String relativePath) {
    return workspace().apiCaseJsonFile(caseName, relativePath);
  }

  /**
   * Reads a shared JSON object that the test can modify before sending.
   *
   * @param relativePath path relative to the global fixture directory
   * @return independent mutable JSON object
   */
  protected final ObjectNode globalJsonObject(String relativePath) {
    return requireObject(workspace().readJson(globalFile(relativePath)), relativePath);
  }

  /**
   * Reads a case-owned JSON object that the test can modify before sending.
   *
   * @param caseName case directory name
   * @param relativePath path relative to the case fixture directory
   * @return independent mutable JSON object
   */
  protected final ObjectNode caseJsonObject(String caseName, String relativePath) {
    return requireObject(
        workspace().readJson(caseFile(caseName, relativePath)), caseName + "/" + relativePath);
  }

  /**
   * Resolves workspace/environment/override variables and returns a JSON model for jsonBody(...).
   *
   * @param environmentName environment that supplies variables
   * @param relativePath path relative to the global fixture directory
   * @param overrides invocation-level variable overrides
   * @return resolved JSON tree
   */
  protected final JsonNode resolvedGlobalJson(
      String environmentName, String relativePath, Map<String, ?> overrides) {
    return resolvedJson(environmentName, workspace().apiGlobalJsonFile(relativePath), overrides);
  }

  protected final JsonNode resolvedGlobalJson(String environmentName, String relativePath) {
    return resolvedGlobalJson(environmentName, relativePath, Map.of());
  }

  protected final JsonNode resolvedCaseJson(
      String environmentName, String caseName, String relativePath, Map<String, ?> overrides) {
    return resolvedJson(
        environmentName, workspace().apiCaseJsonFile(caseName, relativePath), overrides);
  }

  protected final JsonNode resolvedCaseJson(
      String environmentName, String caseName, String relativePath) {
    return resolvedCaseJson(environmentName, caseName, relativePath, Map.of());
  }

  private JsonNode resolvedJson(String environmentName, Path path, Map<String, ?> overrides) {
    EnvironmentContext context = workspace().environmentContext(environmentName, overrides);
    return new VariableResolver(workspace().jsonMapper())
        .resolve(workspace().readJson(path), context.variables());
  }

  protected final TestWorkspace workspace() {
    if (workspace == null) {
      workspace = new TestWorkspace(WorkspaceLocator.locate(null));
    }
    return workspace;
  }

  private ObjectNode requireObject(JsonNode value, String source) {
    if (!(value instanceof ObjectNode object)) {
      throw new IllegalArgumentException("JSON fixture must contain an object: " + source);
    }
    return object;
  }
}
