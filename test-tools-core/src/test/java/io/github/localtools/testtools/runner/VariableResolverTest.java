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

    @Test
    void safelyEscapesVariablesInsideJsonStringsAndPreservesNonStringTypes() throws Exception {
        var input = new ObjectMapper().readTree("""
                {"message":"hello ${name}","count":2,"items":["${name}"]}
                """);

        var result = resolver.resolve(input, Map.of("name", "a\"b\nline"));

        assertEquals("hello a\"b\nline", result.path("message").asText());
        assertEquals(2, result.path("count").asInt());
        assertEquals("a\"b\nline", result.path("items").path(0).asText());
    }
}
