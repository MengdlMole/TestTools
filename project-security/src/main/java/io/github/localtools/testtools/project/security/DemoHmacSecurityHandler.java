package io.github.localtools.testtools.project.security;

import io.github.localtools.testtools.http.MutableRequest;
import io.github.localtools.testtools.http.MutableResponse;
import io.github.localtools.testtools.http.RequestSnapshot;
import io.github.localtools.testtools.http.ResponseSnapshot;
import io.github.localtools.testtools.security.HttpSecurityHandler;
import io.github.localtools.testtools.security.SignContext;
import io.github.localtools.testtools.security.VerificationResult;
import io.github.localtools.testtools.security.crypto.HmacSha256;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/** Example project protocol used by the local signed-echo fixtures. */
public final class DemoHmacSecurityHandler implements HttpSecurityHandler {
    @Override public String id() { return "demoHmacSha256"; }

    @Override
    public void signRequest(SignContext context, MutableRequest request) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String canonical = request.method() + "\n" + request.uri().getRawPath()
                + "\n" + timestamp + "\n" + request.bodyText();
        request.header("X-App-Key", context.secret("appKey"));
        request.header("X-Timestamp", timestamp);
        request.header("X-Signature", HmacSha256.signHex(context.secret("appSecret"), canonical));
    }

    @Override
    public VerificationResult verifyResponse(SignContext context, RequestSnapshot request,
                                             ResponseSnapshot response) {
        String actual = response.firstHeader("X-Signature");
        String expected = HmacSha256.signHex(context.secret("appSecret"),
                response.status() + "\n" + response.bodyText());
        return secureEquals(expected, actual) ? VerificationResult.ok()
                : VerificationResult.failed("response signature mismatch");
    }

    @Override
    public VerificationResult verifyMockRequest(SignContext context, RequestSnapshot request) {
        String timestamp = request.firstHeader("X-Timestamp");
        String actual = request.firstHeader("X-Signature");
        String canonical = request.method() + "\n" + request.uri().getRawPath()
                + "\n" + timestamp + "\n" + request.bodyText();
        String expected = HmacSha256.signHex(context.secret("appSecret"), canonical);
        return secureEquals(expected, actual) ? VerificationResult.ok()
                : VerificationResult.failed("request signature mismatch");
    }

    @Override
    public void signMockResponse(SignContext context, RequestSnapshot request, MutableResponse response) {
        response.header("X-Signature",
                HmacSha256.signHex(context.secret("appSecret"),
                        response.status() + "\n" + response.bodyText()));
    }

    private boolean secureEquals(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
