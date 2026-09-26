package io.github.mengdlmole.testtools.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SecurityHandlerRegistryTest {
  @Test
  void resolvesRegisteredHandlerById() {
    SecurityHandlerRegistry registry =
        new SecurityHandlerRegistry(List.of(new NoSecurityHandler()));
    assertEquals("none", registry.byId("none").id());
  }

  @Test
  void reportsAvailableHandlersForUnknownId() {
    SecurityHandlerRegistry registry =
        new SecurityHandlerRegistry(List.of(new NoSecurityHandler()));
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> registry.byId("missing"));
    assertTrue(error.getMessage().contains("none"));
  }
}
