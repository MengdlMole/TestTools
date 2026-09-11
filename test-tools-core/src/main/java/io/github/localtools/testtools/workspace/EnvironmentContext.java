package io.github.localtools.testtools.workspace;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Resolved environment with the shared variable precedence and selected secrets. */
public record EnvironmentContext(EnvironmentConfig environment,
                                 Map<String, String> variables,
                                 Map<String, String> secrets) {
    public EnvironmentContext {
        if (environment == null) throw new IllegalArgumentException("Environment is required");
        variables = variables == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        secrets = secrets == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(secrets));
    }
}
