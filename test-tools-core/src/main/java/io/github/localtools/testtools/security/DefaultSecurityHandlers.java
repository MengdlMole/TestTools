package io.github.localtools.testtools.security;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/** Central extension point shared by the API runner and mock server. */
public final class DefaultSecurityHandlers {
    private DefaultSecurityHandlers() {}

    public static SecurityHandlerRegistry create() {
        List<HttpSecurityHandler> handlers = new ArrayList<>();
        handlers.add(new NoSecurityHandler());
        handlers.add(new DemoHmacSecurityHandler());
        handlers.add(new QueryBodyHmacSecurityHandler());
        // External extension jars can register HttpSecurityHandler through Java ServiceLoader.
        ServiceLoader.load(HttpSecurityHandler.class).forEach(handlers::add);
        return new SecurityHandlerRegistry(handlers);
    }
}
