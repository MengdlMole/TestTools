package io.github.localtools.testtools.apitest;

import io.github.localtools.testtools.http.ApiTestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Start here: a normal JUnit test that can be run or debugged one method at a time. */
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
        ApiTestClient.ApiResponse response = client.get("/health")
                .header("X-Test-Case", "health")
                .executeVerified();

        assertEquals(200, response.status());
        assertEquals("UP", response.json().path("status").asText());
    }
}
