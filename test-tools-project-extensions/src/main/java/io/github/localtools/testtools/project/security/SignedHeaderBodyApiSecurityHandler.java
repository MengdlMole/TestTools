package io.github.localtools.testtools.project.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.localtools.testtools.http.HttpModels.MutableRequest;
import io.github.localtools.testtools.http.HttpModels.RequestSnapshot;
import io.github.localtools.testtools.security.HttpSecurityHandler;
import io.github.localtools.testtools.security.SignContext;
import io.github.localtools.testtools.security.VerificationResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Project-owned security protocol for POST /signed/header-body only.
 * Copy this class for another API instead of adding business rules to core.
 */
public final class SignedHeaderBodyApiSecurityHandler implements HttpSecurityHandler {
    private static final String METHOD = "POST";
    private static final String PATH = "/signed/header-body";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override public String id() { return "signedHeaderBodyApi"; }

    @Override
    public void signRequest(SignContext context, MutableRequest request) {
        requireTarget(request.method(), request.uri().getPath());
        String signData = buildSignData(request.headers(), request.bodyText());
        request.header("X-App-Key", context.secret("appKey"));
        request.header("X-Signature", hmacSha256(context.secret("appSecret"), signData));
    }

    @Override
    public VerificationResult verifyMockRequest(SignContext context, RequestSnapshot request) {
        try {
            requireTarget(request.method(), request.uri().getPath());
            String signData = buildSignData(Map.of(
                    "tranId", requiredHeader(request, "tranId"),
                    "timestamp", requiredHeader(request, "timestamp")), request.bodyText());
            String expected = hmacSha256(context.secret("appSecret"), signData);
            String actual = request.firstHeader("X-Signature");
            return secureEquals(expected, actual) ? VerificationResult.ok()
                    : VerificationResult.failed("request signature mismatch");
        } catch (IllegalArgumentException error) {
            return VerificationResult.failed(error.getMessage());
        }
    }

    static String buildSignData(Map<String, String> headers, String bodyString) {
        String tranId = requiredHeader(headers, "tranId");
        String timestamp = requiredHeader(headers, "timestamp");
        String name = requiredJsonField(bodyString, "name");
        return "tranId" + tranId + "timestamp" + timestamp + "name" + name;
    }

    private static void requireTarget(String method, String path) {
        if (!METHOD.equalsIgnoreCase(method) || !PATH.equals(path)) {
            throw new IllegalArgumentException("signedHeaderBodyApi can only be used for " + METHOD + " " + PATH);
        }
    }

    private static String requiredHeader(Map<String, String> headers, String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing required header: " + name));
    }

    private static String requiredHeader(RequestSnapshot request, String name) {
        String value = request.firstHeader(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing required header: " + name);
        return value;
    }

    private static String requiredJsonField(String bodyString, String name) {
        try {
            JsonNode body = JSON.readTree(bodyString);
            JsonNode value = body == null ? null : body.get(name);
            if (value == null || value.isNull() || !value.isValueNode()) {
                throw new IllegalArgumentException("Missing scalar JSON body field: " + name);
            }
            return value.asText();
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("Request body is not valid JSON", error);
        }
    }

    private String hmacSha256(String secret, String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Cannot calculate HMAC-SHA256", error);
        }
    }

    private boolean secureEquals(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
