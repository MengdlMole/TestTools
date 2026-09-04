package io.github.localtools.testtools.security;

import java.util.Map;

public record SignContext(Map<String, String> variables, Map<String, String> secrets) {
    public String variable(String name) { return variables.get(name); }
    public String secret(String name) {
        String value = secrets.get(name);
        if (value == null) throw new IllegalArgumentException("Missing secret: " + name);
        return value;
    }
}
