package io.github.localtools.testtools.security;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SecurityHandlerRegistry {
    private final Map<String, HttpSecurityHandler> handlers;

    public SecurityHandlerRegistry(List<HttpSecurityHandler> handlers) {
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(HttpSecurityHandler::id, Function.identity()));
    }

    public HttpSecurityHandler byId(String id) {
        HttpSecurityHandler handler = handlers.get(id);
        if (handler == null) throw new IllegalArgumentException("Unknown security handler '" + id + "'. Available: " + handlers.keySet());
        return handler;
    }

    public List<String> ids() { return handlers.keySet().stream().sorted().toList(); }

}
