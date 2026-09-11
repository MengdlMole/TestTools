package io.github.mengdlmole.testtools.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record SignContext(Map<String, String> variables, Map<String, String> secrets) {
    public SignContext {
        variables = variables == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        secrets = secrets == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(secrets));
    }

    public String variable(String name) { return variables.get(name); }
    public String secret(String name) {
        String value = secrets.get(name);
        if (value == null) throw new IllegalArgumentException("Missing secret: " + name);
        return value;
    }
}
