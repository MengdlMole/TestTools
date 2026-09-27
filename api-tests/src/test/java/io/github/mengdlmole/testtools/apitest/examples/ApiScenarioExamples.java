package io.github.mengdlmole.testtools.apitest.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.mengdlmole.testtools.apitest.support.ApiTestSupport;
import io.github.mengdlmole.testtools.http.ApiRequest;
import io.github.mengdlmole.testtools.http.ApiResponse;
import io.github.mengdlmole.testtools.http.ApiTestClient;
import io.github.mengdlmole.testtools.security.crypto.HmacSha256;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Related API calls kept together as normal Java control flow in one JUnit test.
 */
public final class ApiScenarioExamples extends ApiTestSupport {
  @Test
  void newFeatureScenario() {
    int repeat = Integer.getInteger("testtools.repeat", 1);
    for (int iteration = 1; iteration <= repeat; iteration++) {
      runScenario(iteration);
    }
  }

  private void runScenario(int iteration) {
    ApiTestClient client = api("local");

    ApiRequest healthRequest = client.get("/health");
    ApiResponse healthResponse = client.send(healthRequest);
    assertEquals(200, healthResponse.status());

    ApiRequest echoRequest = client.post("/signed/echo");
    echoRequest.jsonBody(
        Map.of("message", "scenario iteration " + iteration, "tenantId", "local-tenant"));
    signEchoRequest(echoRequest);

    ApiResponse echoResponse = client.send(echoRequest);
    assertEquals(200, echoResponse.status());
    assertEquals("local-tenant", echoResponse.jsonPath("$.echo.tenantId"));

    // Put scenario cleanup in a try/finally block when the real flow creates persistent data.
  }

  private void signEchoRequest(ApiRequest request) {
    String timestamp = String.valueOf(Instant.now().getEpochSecond());
    // /signed/echo contract, not a rule imposed by ApiRequest.
    String contentToSign = "POST\n/signed/echo\n" + timestamp + "\n" + request.bodyText();
    request.header("X-App-Key", secret("local", "appKey"));
    request.header("X-Timestamp", timestamp);
    request.header("X-Signature", HmacSha256.signHex(secret("local", "appSecret"), contentToSign));
  }
}
