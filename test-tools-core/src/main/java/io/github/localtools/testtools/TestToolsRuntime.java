package io.github.localtools.testtools;

import io.github.localtools.testtools.http.HttpExecutor;
import io.github.localtools.testtools.runner.AssertionEngine;
import io.github.localtools.testtools.runner.AutomationRunner;
import io.github.localtools.testtools.runner.TestCaseRunner;
import io.github.localtools.testtools.runner.VariableResolver;
import io.github.localtools.testtools.security.DefaultSecurityHandlers;
import io.github.localtools.testtools.security.SecurityHandlerRegistry;
import io.github.localtools.testtools.workspace.WorkspaceService;

import java.nio.file.Path;

/** Dependency-free composition root used by both local applications. */
public record TestToolsRuntime(WorkspaceService workspace,
                               VariableResolver variables,
                               SecurityHandlerRegistry securityHandlers,
                               AutomationRunner automationRunner) {
    public static TestToolsRuntime open(Path workspacePath) {
        WorkspaceService workspace = new WorkspaceService(workspacePath);
        VariableResolver variables = new VariableResolver(workspace.jsonMapper());
        SecurityHandlerRegistry handlers = DefaultSecurityHandlers.create();
        TestCaseRunner caseRunner = new TestCaseRunner(workspace, variables, handlers,
                new HttpExecutor(), new AssertionEngine(workspace.jsonMapper()));
        return new TestToolsRuntime(workspace, variables, handlers,
                new AutomationRunner(caseRunner, workspace));
    }
}
