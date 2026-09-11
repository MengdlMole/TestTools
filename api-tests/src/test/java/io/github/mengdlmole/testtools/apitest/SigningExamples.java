package io.github.mengdlmole.testtools.apitest;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mengdlmole.testtools.http.ApiTestClient;
import io.github.mengdlmole.testtools.http.MutableRequest;
import io.github.mengdlmole.testtools.security.VerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Per-API inline signing and reusable project security handlers. */
public final class SigningExamples extends ApiTestSupport {
    private ApiTestClient client;

    @BeforeEach
    void setUp() { client = api("local"); }

    @Test
    void signsAndVerifiesInlineForOneApi() {
        String appKey = secret("local", "appKey");
        String appSecret = secret("local", "appSecret");

        ApiTestClient.ApiResponse response = client.post("/signed/echo")
                .jsonBody(Map.of("message", "hello from local-test-tools", "tenantId", "local-tenant"))
                .signWith(request -> {
                    String timestamp = String.valueOf(Instant.now().getEpochSecond());
                    String signData = request.method() + "\n" + request.uri().getRawPath()
                            + "\n" + timestamp + "\n" + request.bodyText();
                    request.header("X-App-Key", appKey);
                    request.header("X-Timestamp", timestamp);
                    request.header("X-Signature", hmacSha256(appSecret, signData));
                })
                .verifyWith((request, httpResponse) -> {
                    String expected = hmacSha256(appSecret,
                            httpResponse.status() + "\n" + httpResponse.bodyText());
                    return expected.equals(httpResponse.firstHeader("X-Signature"))
                            ? VerificationResult.ok()
                            : VerificationResult.failed("response signature mismatch");
                })
                .executeVerified();

        assertEquals(200, response.status());
    }

    @Test
    void usesReusableProjectSecurityHandler() {
        ApiTestClient.ApiResponse response = client.post("/signed/echo")
                .jsonBody(Map.of("message", "hello from local-test-tools", "tenantId", "local-tenant"))
                .security(securityHandler("demoHmacSha256"), signContext("local"))
                .executeVerified();
        assertEquals(200, response.status());
    }

    @Test
    void signsRawQueryAndBodyWithReusableHandler() {
        ApiTestClient.ApiResponse response = client.post("/signed/query-body")
                .query("z", "last")
                .query("name", "value with space")
                .query("name", "same-key-again")
                .jsonBody(Map.of("quantity", 2, "productId", "P1001"))
                .security(securityHandler("queryBodyHmacSha256"), signContext("local"))
                .executeVerified();
        assertEquals("sortedRawQueryPlusExactBody", response.jsonPath("$.signData"));
    }

    @Test
    void signsSelectedHeaderAndJsonBodyFieldsInline() {
        String appKey = secret("local", "appKey");
        String appSecret = secret("local", "appSecret");

        ApiTestClient.ApiResponse response = client.post("/signed/header-body")
                .header("tranId", "111")
                .header("timestamp", "222")
                .jsonBodyFile(globalJson("header-body-request.json"))
                .signWith(request -> {
                    String signData = headerBodySignData(request);
                    request.header("X-App-Key", appKey);
                    request.header("X-Signature", hmacSha256(appSecret, signData));
                })
                .executeVerified();

        assertEquals("tranId111timestamp222name333", response.jsonPath("$.canonical"));
    }

    private String headerBodySignData(MutableRequest request) {
        try {
            String tranId = requiredHeader(request, "tranId");
            String timestamp = requiredHeader(request, "timestamp");
            JsonNode name = workspace().jsonMapper().readTree(request.body()).get("name");
            if (name == null || !name.isValueNode()) {
                throw new IllegalArgumentException("Missing scalar JSON body field: name");
            }
            return "tranId" + tranId + "timestamp" + timestamp + "name" + name.asText();
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("Cannot build signature data for POST /signed/header-body", error);
        }
    }

    private String requiredHeader(MutableRequest request, String name) {
        return request.headers().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing required header: " + name));
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
