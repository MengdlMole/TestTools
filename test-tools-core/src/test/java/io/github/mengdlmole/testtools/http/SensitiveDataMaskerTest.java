package io.github.mengdlmole.testtools.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SensitiveDataMaskerTest {
  @Test
  void masksUriUserInfoAndSensitiveQueryParameters() {
    assertEquals(
        "https://***@example.test/orders?token=***&name=visible#result",
        SensitiveDataMasker.maskUri(
            "https://user:password@example.test/orders?token=secret&name=visible#result"));
  }

  @Test
  void leavesRelativeUrisWithoutCredentialsUsable() {
    assertEquals(
        "/orders?apiKey=***&name=visible",
        SensitiveDataMasker.maskUri("/orders?apiKey=secret&name=visible"));
  }
}
