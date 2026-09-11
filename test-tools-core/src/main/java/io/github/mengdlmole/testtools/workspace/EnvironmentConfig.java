package io.github.mengdlmole.testtools.workspace;

import java.util.Map;

/** One callable target environment and its local secret reference. */
public record EnvironmentConfig(String name, String baseUrl, String secretRef,
                                String defaultSecurityHandler, Map<String, String> variables) {}
