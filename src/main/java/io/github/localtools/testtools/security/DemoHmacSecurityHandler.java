package io.github.localtools.testtools.security;

import io.github.localtools.testtools.http.HttpModels.MutableRequest;
import io.github.localtools.testtools.http.HttpModels.MutableResponse;
import io.github.localtools.testtools.http.HttpModels.RequestSnapshot;
import io.github.localtools.testtools.http.HttpModels.ResponseSnapshot;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Component
public class DemoHmacSecurityHandler implements ApiSecurityHandler {
    @Override public String id() { return "demoHmacSha256"; }

    @Override
    public void signRequest(SignContext context, MutableRequest request) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String canonical = request.method() + "\n" + request.uri().getRawPath() + "\n" + timestamp + "\n" + request.bodyText();
        request.header("X-App-Key", context.secret("appKey"));
        request.header("X-Timestamp", timestamp);
        request.header("X-Signature", hmac(context.secret("appSecret"), canonical));
    }

    @Override
    public VerificationResult verifyResponse(SignContext context, RequestSnapshot request, ResponseSnapshot response) {
        String actual = response.firstHeader("X-Signature");
        String expected = hmac(context.secret("appSecret"), response.status() + "\n" + response.bodyText());
        return secureEquals(expected, actual) ? VerificationResult.ok() : VerificationResult.failed("response signature mismatch");
    }

    @Override
    public VerificationResult verifyMockRequest(SignContext context, RequestSnapshot request) {
        String timestamp = request.firstHeader("X-Timestamp");
        String actual = request.firstHeader("X-Signature");
        String canonical = request.method() + "\n" + request.uri().getRawPath() + "\n" + timestamp + "\n" + request.bodyText();
        String expected = hmac(context.secret("appSecret"), canonical);
        return secureEquals(expected, actual) ? VerificationResult.ok() : VerificationResult.failed("request signature mismatch");
    }

    @Override
    public void signMockResponse(SignContext context, RequestSnapshot request, MutableResponse response) {
        response.header("X-Signature", hmac(context.secret("appSecret"), response.status() + "\n" + response.bodyText()));
    }

    private String hmac(String secret, String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot calculate HMAC-SHA256", e);
        }
    }

    private boolean secureEquals(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
