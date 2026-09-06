package io.github.localtools.testtools.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VariableResolverTest {
    private final VariableResolver resolver = new VariableResolver(new ObjectMapper());

    @Test
    void replacesMultipleVariables() {
        assertEquals("http://localhost/users/42",
                resolver.resolve("${base}/users/${id}", Map.of("base", "http://localhost", "id", "42")));
    }
}
