package io.github.mengdlmole.testtools.apitest;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mengdlmole.testtools.http.ApiTestClient;
import io.github.mengdlmole.testtools.workspace.VariableResolver;
import io.github.mengdlmole.testtools.security.SecurityHandlerLoader;
import io.github.mengdlmole.testtools.security.HttpSecurityHandler;
import io.github.mengdlmole.testtools.security.SecurityHandlerRegistry;
import io.github.mengdlmole.testtools.security.SignContext;
import io.github.mengdlmole.testtools.workspace.EnvironmentConfig;
import io.github.mengdlmole.testtools.workspace.TestWorkspace;

import java.nio.file.Path;
import java.util.Map;
import io.github.mengdlmole.testtools.workspace.EnvironmentContext;
import io.github.mengdlmole.testtools.workspace.WorkspaceLocator;

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
        EnvironmentContext context = workspace().environmentContext(environmentName);
        return new SignContext(context.variables(), context.secrets());
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
        EnvironmentContext context = workspace().environmentContext(environmentName, overrides);
        return new VariableResolver(workspace().jsonMapper()).resolve(workspace().readJson(path), context.variables());
    }

    protected final TestWorkspace workspace() {
        if (workspace == null) workspace = new TestWorkspace(WorkspaceLocator.locate(null));
        return workspace;
    }
}
