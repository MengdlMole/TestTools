package io.github.mengdlmole.testtools.workspace;

import java.util.Map;

/**
 * Settings shared by every tool that uses the local test workspace.
 *
 * @param defaultEnvironment environment selected when a runner does not specify one
 * @param variables workspace-level variables
 * @param maskSensitiveData whether API-test logs hide credentials and sensitive headers
 */
public record WorkspaceConfig(
    String defaultEnvironment, Map<String, String> variables, Boolean maskSensitiveData) {
  public boolean resolvedMaskSensitiveData() {
    return maskSensitiveData == null || maskSensitiveData;
  }
}
