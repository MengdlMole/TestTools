package io.github.localtools.testtools.runnercli;

import io.github.localtools.testtools.http.ApiTestClient;
import io.github.localtools.testtools.security.DefaultSecurityHandlers;
import io.github.localtools.testtools.security.HttpSecurityHandler;
import io.github.localtools.testtools.security.SecurityHandlerRegistry;
import io.github.localtools.testtools.security.SignContext;
import io.github.localtools.testtools.workspace.WorkspaceModels.EnvironmentConfig;
import io.github.localtools.testtools.workspace.WorkspaceService;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Common setup helpers for code-first JUnit or TestNG API test classes. */
public abstract class ApiTestSupport {
    private WorkspaceService workspace;
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
        if (handlers == null) handlers = DefaultSecurityHandlers.create();
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

    protected final WorkspaceService workspace() {
        if (workspace == null) workspace = new WorkspaceService(RunnerSettings.workspace);
        return workspace;
    }
}
