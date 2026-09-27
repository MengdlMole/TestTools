package io.github.mengdlmole.testtools.mock.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MockCallStoreTest {
  @Test
  void masksSensitiveUriDataByDefaultConfiguration() {
    MockCallStore calls = new MockCallStore(true);

    calls.record("GET", "/orders?token=secret", 200, "orders");

    assertEquals("/orders?token=***", calls.recent().getFirst().path());
  }

  @Test
  void canPreserveRawUriDataForExplicitLocalDebugging() {
    MockCallStore calls = new MockCallStore(false);

    calls.record("GET", "/orders?token=secret", 200, "orders");

    assertEquals("/orders?token=secret", calls.recent().getFirst().path());
  }
}
