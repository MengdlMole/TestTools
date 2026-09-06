package io.github.localtools.testtools.security;

import io.github.localtools.testtools.workspace.WorkspaceModels.SecurityRule;
import io.github.localtools.testtools.workspace.WorkspaceModels.TestStep;
import io.github.localtools.testtools.workspace.WorkspaceModels.WorkspaceConfig;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SecurityHandlerRegistry {
    private final Map<String, HttpSecurityHandler> handlers;

    public SecurityHandlerRegistry(List<HttpSecurityHandler> handlers) {
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(HttpSecurityHandler::id, Function.identity()));
    }

    public HttpSecurityHandler resolve(TestStep step, WorkspaceConfig config, String environmentDefault) {
        if (hasText(step.securityHandler())) return byId(step.securityHandler());
        if (config.securityRules() != null) {
            for (SecurityRule rule : config.securityRules()) {
                if (matches(rule, step)) return byId(rule.handler());
            }
        }
        return byId(hasText(environmentDefault) ? environmentDefault : "none");
    }

    public HttpSecurityHandler byId(String id) {
        HttpSecurityHandler handler = handlers.get(id);
        if (handler == null) throw new IllegalArgumentException("Unknown security handler '" + id + "'. Available: " + handlers.keySet());
        return handler;
    }

    public List<String> ids() { return handlers.keySet().stream().sorted().toList(); }

    private boolean matches(SecurityRule rule, TestStep step) {
        if (hasText(rule.operationId()) && !rule.operationId().equals(step.operationId())) return false;
        if (hasText(rule.method()) && !rule.method().equalsIgnoreCase(step.method())) return false;
        return !hasText(rule.pathPattern()) || glob(rule.pathPattern()).matcher(step.path()).matches();
    }

    private Pattern glob(String value) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '*' && i + 1 < value.length() && value.charAt(i + 1) == '*') {
                regex.append(".*");
                i++;
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
