package io.github.localtools.testtools.apitest;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.localtools.testtools.http.ApiTestClient;
import io.github.localtools.testtools.workspace.VariableResolver;
import io.github.localtools.testtools.security.SecurityHandlerLoader;
import io.github.localtools.testtools.security.HttpSecurityHandler;
import io.github.localtools.testtools.security.SecurityHandlerRegistry;
import io.github.localtools.testtools.security.SignContext;
import io.github.localtools.testtools.workspace.EnvironmentConfig;
import io.github.localtools.testtools.workspace.TestWorkspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Common setup helpers for code-first JUnit API test classes. */
public abstract class ApiTestSupport {
    private TestWorkspace workspace;
    private SecurityHandlerRegistry handlers;

    protected final ApiScenario scenario(String name) {
        return new ApiScenario(name);
    }

    protected final ApiTestClient api(String environmentName) {
        EnvironmentConfig environment = workspace().environment(environmentName);
        return ApiTestClient.builder(environment.baseUrl())
                .objectMapper(workspace().jsonMapper())
                .build();
    }

    protected final HttpSecurityHandler securityHandler(String id) {
        if (handlers == null) handlers = SecurityHandlerLoader.create();
        return handlers.byId(id);
    }

    protected final SignContext signContext(String environmentName) {
        EnvironmentConfig environment = workspace().environment(environmentName);
        Map<String, String> variables = new LinkedHashMap<>();
        if (workspace().config().variables() != null) variables.putAll(workspace().config().variables());
        if (environment.variables() != null) variables.putAll(environment.variables());
        return new SignContext(Map.copyOf(variables), workspace().secrets(environment.secretRef()));
    }

    protected final String secret(String environmentName, String name) {
        return signContext(environmentName).secret(name);
    }

    protected final Path fixture(String relativePath) {
        Path path = workspace().root().resolve(relativePath).normalize();
        if (!path.startsWith(workspace().root())) throw new IllegalArgumentException("Fixture must remain inside workspace");
        return path;
    }

    /** Shared JSON body stored in test-workspace/fixtures/global. */
    protected final Path globalJson(String relativePath) {
        return workspace().globalJsonFile(relativePath);
    }

    /** Case-owned JSON body stored in test-workspace/fixtures/cases/{caseName}. */
    protected final Path caseJson(String caseName, String relativePath) {
        return workspace().caseJsonFile(caseName, relativePath);
    }

    /** Resolves workspace/environment/override variables and returns a JSON model for jsonBody(...). */
    protected final JsonNode resolvedGlobalJson(String environmentName, String relativePath,
                                                Map<String, ?> overrides) {
        return resolvedJson(environmentName, workspace().globalJsonFile(relativePath), overrides);
    }

    protected final JsonNode resolvedGlobalJson(String environmentName, String relativePath) {
        return resolvedGlobalJson(environmentName, relativePath, Map.of());
    }

    protected final JsonNode resolvedCaseJson(String environmentName, String caseName,
                                              String relativePath, Map<String, ?> overrides) {
        return resolvedJson(environmentName, workspace().caseJsonFile(caseName, relativePath), overrides);
    }

    protected final JsonNode resolvedCaseJson(String environmentName, String caseName, String relativePath) {
        return resolvedCaseJson(environmentName, caseName, relativePath, Map.of());
    }

    private JsonNode resolvedJson(String environmentName, Path path, Map<String, ?> overrides) {
        EnvironmentConfig environment = workspace().environment(environmentName);
        Map<String, String> values = new LinkedHashMap<>();
        if (workspace().config().variables() != null) values.putAll(workspace().config().variables());
        if (environment.variables() != null) values.putAll(environment.variables());
        if (overrides != null) {
            overrides.forEach((name, value) -> values.put(name, value == null ? "" : String.valueOf(value)));
        }
        return new VariableResolver(workspace().jsonMapper()).resolve(workspace().readJson(path), values);
    }

    protected final TestWorkspace workspace() {
        if (workspace == null) workspace = new TestWorkspace(workspacePath());
        return workspace;
    }

    private Path workspacePath() {
        String configured = System.getProperty("testtools.workspace");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured.trim()).toAbsolutePath().normalize();
        }
        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            Path workspace = candidate.resolve("test-workspace");
            if (Files.isRegularFile(workspace.resolve("workspace.yaml"))) return workspace;
        }
        return current.resolve("test-workspace");
    }
}
