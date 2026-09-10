package io.github.localtools.testtools.yamlrunner.engine;

import io.github.localtools.testtools.http.HttpExecutor;
import io.github.localtools.testtools.workspace.VariableResolver;
import io.github.localtools.testtools.security.SecurityHandlerLoader;
import io.github.localtools.testtools.security.SecurityHandlerRegistry;
import io.github.localtools.testtools.workspace.TestWorkspace;
import io.github.localtools.testtools.yamlrunner.YamlSecurityResolver;
import io.github.localtools.testtools.yamlrunner.YamlWorkspace;

import java.nio.file.Path;

/** Dependency-free composition root used by both local applications. */
public record YamlTestRuntime(YamlWorkspace workspace,
                              VariableResolver variables,
                              SecurityHandlerRegistry securityHandlers,
                              YamlSuiteRunner suiteRunner) {
    public static YamlTestRuntime open(Path workspacePath) {
        TestWorkspace files = new TestWorkspace(workspacePath);
        YamlWorkspace workspace = new YamlWorkspace(files);
        VariableResolver variables = new VariableResolver(files.jsonMapper());
        SecurityHandlerRegistry handlers = SecurityHandlerLoader.create();
        YamlSecurityResolver security = new YamlSecurityResolver(handlers, workspace.config().securityRules());
        YamlCaseRunner caseRunner = new YamlCaseRunner(workspace, variables, security,
                new HttpExecutor(), new YamlAssertionEngine(files.jsonMapper()));
        return new YamlTestRuntime(workspace, variables, handlers,
                new YamlSuiteRunner(caseRunner, workspace));
    }
}
