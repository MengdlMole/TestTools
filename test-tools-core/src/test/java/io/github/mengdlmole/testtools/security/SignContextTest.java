package io.github.mengdlmole.testtools.security;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignContextTest {
    @Test
    void keepsAnImmutableCopyOfVariablesAndSecrets() {
        Map<String, String> variables = new LinkedHashMap<>(Map.of("tenant", "one"));
        SignContext context = new SignContext(variables, Map.of("appSecret", "secret"));
        variables.put("tenant", "two");

        assertEquals("one", context.variable("tenant"));
        assertEquals("secret", context.secret("appSecret"));
        assertThrows(UnsupportedOperationException.class,
                () -> context.variables().put("other", "value"));
    }
}
