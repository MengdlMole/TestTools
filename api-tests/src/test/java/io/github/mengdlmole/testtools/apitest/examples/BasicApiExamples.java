package io.github.mengdlmole.testtools.apitest.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.mengdlmole.testtools.apitest.support.ApiTestSupport;
import io.github.mengdlmole.testtools.http.ApiRequest;
import io.github.mengdlmole.testtools.http.ApiResponse;
import io.github.mengdlmole.testtools.http.ApiTestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * Start here: a normal JUnit test that can be run or debugged one method at a time.
 */
public final class BasicApiExamples extends ApiTestSupport {
  private ApiTestClient client;
  private long started;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    started = System.nanoTime();
    client = api("local");
    System.out.println("[before] " + testInfo.getDisplayName());
  }

  @AfterEach
  void tearDown(TestInfo testInfo) {
    long durationMs = (System.nanoTime() - started) / 1_000_000;
    System.out.println("[after] " + testInfo.getDisplayName() + ", total " + durationMs + " ms");
  }

  @Test
  void health() {
    ApiRequest request = client.get("/health");
    request.header("X-Test-Case", "health");

    ApiResponse response = client.send(request);

    assertEquals(200, response.status());
    assertEquals("UP", response.json().path("status").asText());
  }
}
