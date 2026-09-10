package io.github.localtools.testtools.yamlrunner;

import io.github.localtools.testtools.security.HttpSecurityHandler;
import io.github.localtools.testtools.security.SecurityHandlerRegistry;
import io.github.localtools.testtools.yamlrunner.model.YamlRunnerConfig.SecurityRule;
import io.github.localtools.testtools.yamlrunner.model.YamlStepDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YamlSecurityResolverTest {
    private final SecurityHandlerRegistry handlers = new SecurityHandlerRegistry(List.of(
            new NamedHandler("none"), new NamedHandler("explicit"),
            new NamedHandler("operation"), new NamedHandler("path")));

    @Test
    void explicitHandlerPrecedesRulesAndEnvironmentDefault() {
        YamlSecurityResolver resolver = resolver();
        assertEquals("explicit", resolver.resolve(step("createOrder", "/orders", "explicit"), "none").id());
    }

    @Test
    void operationRulePrecedesPathRuleAndDoubleStarMatchesMultipleSegments() {
        YamlSecurityResolver resolver = resolver();
        assertEquals("operation", resolver.resolve(step("createOrder", "/legacy/orders", null), "none").id());
        assertEquals("path", resolver.resolve(step(null, "/legacy/orders/1", null), "none").id());
    }

    private YamlSecurityResolver resolver() {
        return new YamlSecurityResolver(handlers, List.of(
                new SecurityRule("createOrder", null, null, "operation"),
                new SecurityRule(null, "POST", "/legacy/**", "path")));
    }

    private YamlStepDefinition step(String operationId, String path, String handler) {
        return new YamlStepDefinition("step", operationId, "http", "POST", path,
                Map.of(), Map.of(), null, null, null, null, handler, Map.of(), List.of());
    }

    private record NamedHandler(String id) implements HttpSecurityHandler {}
}
