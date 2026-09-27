package io.github.mengdlmole.testtools.apitest.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mengdlmole.testtools.apitest.support.ApiTestSupport;
import io.github.mengdlmole.testtools.http.ApiRequest;
import io.github.mengdlmole.testtools.http.ApiResponse;
import io.github.mengdlmole.testtools.http.ApiTestClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Explicit request assembly and per-API signing examples.
 */
public final class SigningExamples extends ApiTestSupport {
  private ApiTestClient client;
  private String appKey;
  private String appSecret;

  @BeforeEach
  void setUp() {
    client = api("local");
    appKey = secret("local", "appKey");
    appSecret = secret("local", "appSecret");
  }

  @Test
  void callsSignedEchoUsingItsDocumentedContract() {
    String method = "POST";
    String path = "/signed/echo";
    ApiRequest request = client.request(method, path);
    request.jsonBody(Map.of("message", "hello from local-test-tools", "tenantId", "local-tenant"));

    String timestamp = String.valueOf(Instant.now().getEpochSecond());
    // /signed/echo contract: METHOD + LF + PATH + LF + TIMESTAMP + LF + exact body.
    String contentToSign = method + "\n" + path + "\n" + timestamp + "\n" + request.bodyText();
    String signature = hmacSha256(appSecret, contentToSign);

    request.header("X-App-Key", appKey);
    request.header("X-Timestamp", timestamp);
    request.header("X-Signature", signature);

    ApiResponse response = client.send(request);

    assertEquals(200, response.status());
    assertEquals(
        hmacSha256(appSecret, response.status() + "\n" + response.body()),
        response.header("X-Signature"));
  }

  @Test
  void signsSelectedHeaderAndJsonFields() {
    ObjectNode body = globalJsonObject("header-body-request.json");
    body.put("name", 333);
    body.put("description", "changed by this test before signing");

    String tranId = "111";
    String timestamp = "222";
    ApiRequest request = client.post("/signed/header-body");
    request.header("tranId", tranId);
    request.header("timestamp", timestamp);
    request.jsonBody(body);

    // /signed/header-body contract: field names and values in this exact order.
    String contentToSign =
        "tranId" + tranId + "timestamp" + timestamp + "name" + body.path("name").asText();
    request.header("X-App-Key", appKey);
    request.header("X-Signature", hmacSha256(appSecret, contentToSign));

    ApiResponse response = client.send(request);

    assertEquals(200, response.status());
    assertEquals("tranId111timestamp222name333", response.jsonPath("$.canonical"));
  }

  @Test
  void signsSortedRawQueryAndExactBody() {
    ApiRequest request = client.post("/signed/query-body");
    request.query("z", "last");
    request.query("name", "value with space");
    request.query("name", "same-key-again");
    request.jsonBody(Map.of("quantity", 2, "productId", "P1001"));

    String sortedRawQuery =
        Arrays.stream(request.uri().getRawQuery().split("&"))
            .sorted()
            .collect(Collectors.joining("&"));
    // /signed/query-body contract: sorted encoded query followed immediately by exact body.
    String contentToSign = sortedRawQuery + request.bodyText();
    request.header("X-App-Key", appKey);
    request.header("X-Signature", hmacSha256(appSecret, contentToSign));

    ApiResponse response = client.send(request);

    assertEquals(200, response.status());
    assertEquals("sortedRawQueryPlusExactBody", response.jsonPath("$.signData"));
    assertEquals(hmacSha256(appSecret, response.body()), response.header("X-Signature"));
  }

  private String hmacSha256(String secret, String content) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      throw new IllegalStateException("Cannot calculate HMAC-SHA256", error);
    }
  }
}
