package io.github.localtools.testtools.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VariableResolverTest {
    private final VariableResolver resolver = new VariableResolver(new ObjectMapper());

    @Test
    void replacesMultipleVariables() {
        assertThat(resolver.resolve("${base}/users/${id}", Map.of("base", "http://localhost", "id", "42")))
                .isEqualTo("http://localhost/users/42");
    }
}
