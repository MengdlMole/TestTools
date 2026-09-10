package io.github.localtools.testtools.yamlrunner;

import io.github.localtools.testtools.security.HttpSecurityHandler;
import io.github.localtools.testtools.security.SecurityHandlerRegistry;
import io.github.localtools.testtools.yamlrunner.model.YamlRunnerConfig.SecurityRule;
import io.github.localtools.testtools.yamlrunner.model.YamlStepDefinition;

import java.util.List;
import java.util.regex.Pattern;

/** Selects a security handler according to YAML-runner rules. */
public final class YamlSecurityResolver {
    private final SecurityHandlerRegistry handlers;
    private final List<SecurityRule> rules;

    public YamlSecurityResolver(SecurityHandlerRegistry handlers, List<SecurityRule> rules) {
        this.handlers = handlers;
        this.rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public HttpSecurityHandler resolve(YamlStepDefinition step, String environmentDefault) {
        if (hasText(step.securityHandler())) return handlers.byId(step.securityHandler());
        for (SecurityRule rule : rules) {
            if (matches(rule, step)) return handlers.byId(rule.handler());
        }
        return handlers.byId(hasText(environmentDefault) ? environmentDefault : "none");
    }

    private boolean matches(SecurityRule rule, YamlStepDefinition step) {
        if (hasText(rule.operationId()) && !rule.operationId().equals(step.operationId())) return false;
        if (hasText(rule.method()) && !rule.method().equalsIgnoreCase(step.method())) return false;
        return !hasText(rule.pathPattern()) || glob(rule.pathPattern()).matcher(step.path()).matches();
    }

    private Pattern glob(String value) {
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '*' && index + 1 < value.length() && value.charAt(index + 1) == '*') {
                regex.append(".*");
                index++;
            } else if (current == '*') {
                regex.append("[^/]*");
            } else {
                regex.append(Pattern.quote(String.valueOf(current)));
            }
        }
        return Pattern.compile(regex.append('$').toString());
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
