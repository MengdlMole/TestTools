package io.github.localtools.testtools.runnercli;

import io.github.localtools.testtools.http.ApiTestClient;
import io.github.localtools.testtools.security.VerificationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Code-first examples: run or debug each method independently in IDEA or VS Code. */
public final class CodeFirstApiExamples extends ApiTestSupport {
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

    @Test
    void signedEchoWithInlineSignature() {
        String appKey = secret("local", "appKey");
        String appSecret = secret("local", "appSecret");

        ApiTestClient.ApiResponse response = client.post("/signed/echo")
                .header("X-Test-Case", "signed-echo")
                .jsonBody(Map.of(
                        "message", "hello from local-test-tools",
                        "tenantId", "local-tenant"
                ))
                .signWith(request -> {
                    String timestamp = String.valueOf(Instant.now().getEpochSecond());
                    String signData = request.method() + "\n"
                            + request.uri().getRawPath() + "\n"
                            + timestamp + "\n"
                            + request.bodyText();
                    request.header("X-App-Key", appKey);
                    request.header("X-Timestamp", timestamp);
                    request.header("X-Signature", hmacSha256(appSecret, signData));
                })
                .verifyWith((request, httpResponse) -> {
                    String signData = httpResponse.status() + "\n" + httpResponse.bodyText();
                    String expected = hmacSha256(appSecret, signData);
                    String actual = httpResponse.firstHeader("X-Signature");
                    return expected.equals(actual) ? VerificationResult.ok()
                            : VerificationResult.failed("response signature mismatch");
                })
                .executeVerified();

        assertEquals(200, response.status());
        assertEquals("hello from local-test-tools", response.jsonPath("$.echo.message"));
    }

    @Test
    void signedEchoWithReusableHandler() {
        ApiTestClient.ApiResponse response = client.post("/signed/echo")
                .jsonBody(Map.of(
                        "message", "hello from local-test-tools",
                        "tenantId", "local-tenant"
                ))
                .security(securityHandler("demoHmacSha256"), signContext("local"))
                .executeVerified();

        assertEquals(200, response.status());
    }

    @Test
    void signedQueryAndBodyWithReusableHandler() {
        ApiTestClient.ApiResponse response = client.post("/signed/query-body")
                .query("z", "last")
                .query("name", "value with space")
                .query("name", "same-key-again")
                .jsonBody(Map.of("quantity", 2, "productId", "P1001"))
                .security(securityHandler("queryBodyHmacSha256"), signContext("local"))
                .executeVerified();

        assertEquals(200, response.status());
        assertEquals("sortedRawQueryPlusExactBody", response.jsonPath("$.signData"));
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
