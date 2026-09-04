package io.github.localtools.testtools.security;

import io.github.localtools.testtools.workspace.WorkspaceModels.SecurityRule;
import io.github.localtools.testtools.workspace.WorkspaceModels.TestStep;
import io.github.localtools.testtools.workspace.WorkspaceModels.WorkspaceConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHandlerRegistryTest {
    private final ApiSecurityHandler none = new NamedHandler("none");
    private final ApiSecurityHandler operation = new NamedHandler("operation");
    private final ApiSecurityHandler path = new NamedHandler("path");
    private final SecurityHandlerRegistry registry = new SecurityHandlerRegistry(List.of(none, operation, path));

    @Test
    void explicitStepHandlerHasHighestPriority() {
        TestStep step = step("createOrder", "POST", "/orders/1", "path");
        WorkspaceConfig config = config(List.of(new SecurityRule("createOrder", null, null, "operation")));
        assertThat(registry.resolve(step, config, "none").id()).isEqualTo("path");
    }

    @Test
    void matchesOperationBeforePathAndSupportsDoubleStar() {
        WorkspaceConfig config = config(List.of(
                new SecurityRule("createOrder", null, null, "operation"),
                new SecurityRule(null, "POST", "/legacy/**", "path")));
        assertThat(registry.resolve(step("createOrder", "POST", "/orders", null), config, "none").id())
                .isEqualTo("operation");
        assertThat(registry.resolve(step(null, "POST", "/legacy/order/1", null), config, "none").id())
                .isEqualTo("path");
    }

    private WorkspaceConfig config(List<SecurityRule> rules) {
        return new WorkspaceConfig("local", 19090, Map.of(), rules);
    }

    private TestStep step(String operationId, String method, String path, String handler) {
        return new TestStep("step", operationId, method, path, Map.of(), Map.of(),
                null, null, handler, Map.of(), List.of());
    }

    private record NamedHandler(String id) implements ApiSecurityHandler {}
}
