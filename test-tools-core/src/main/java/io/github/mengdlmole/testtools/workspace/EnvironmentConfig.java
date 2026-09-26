package io.github.mengdlmole.testtools.workspace;

import java.util.Map;

/**
 * One callable target environment and its local secret reference.
 *
 * @param name environment name
 * @param baseUrl base URL used for relative API paths
 * @param secretRef key of the corresponding entry in the local secrets file
 * @param defaultSecurityHandler default security handler identifier
 * @param variables environment-level variables
 */
public record EnvironmentConfig(
    String name,
    String baseUrl,
    String secretRef,
    String defaultSecurityHandler,
    Map<String, String> variables) {}
