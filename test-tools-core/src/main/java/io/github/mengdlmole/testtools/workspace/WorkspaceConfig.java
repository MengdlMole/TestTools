package io.github.mengdlmole.testtools.workspace;

import java.util.Map;

/** Settings shared by every tool that uses the local test workspace. */
public record WorkspaceConfig(String defaultEnvironment, Map<String, String> variables) {}
