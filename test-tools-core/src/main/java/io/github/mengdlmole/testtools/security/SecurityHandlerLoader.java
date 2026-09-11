package io.github.mengdlmole.testtools.security;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/** Central extension point shared by the API runner and mock server. */
public final class SecurityHandlerLoader {
    private SecurityHandlerLoader() {}

    public static SecurityHandlerRegistry create() {
        List<HttpSecurityHandler> handlers = new ArrayList<>();
        handlers.add(new NoSecurityHandler());
        // Project extension jars register HttpSecurityHandler through Java ServiceLoader.
        ServiceLoader.load(HttpSecurityHandler.class).forEach(handlers::add);
        return new SecurityHandlerRegistry(handlers);
    }
}
