package io.github.mengdlmole.testtools.project.security;

import io.github.mengdlmole.testtools.http.MutableRequest;
import io.github.mengdlmole.testtools.http.MutableResponse;
import io.github.mengdlmole.testtools.http.RequestSnapshot;
import io.github.mengdlmole.testtools.http.ResponseSnapshot;
import io.github.mengdlmole.testtools.security.HttpSecurityHandler;
import io.github.mengdlmole.testtools.security.SignContext;
import io.github.mengdlmole.testtools.security.VerificationResult;
import io.github.mengdlmole.testtools.security.crypto.ConstantTime;
import io.github.mengdlmole.testtools.security.crypto.HmacSha256;

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
        return ConstantTime.equalsUtf8(expected, actual) ? VerificationResult.ok()
                : VerificationResult.failed("response signature mismatch");
    }

    @Override
    public VerificationResult verifyMockRequest(SignContext context, RequestSnapshot request) {
        if (!ConstantTime.equalsUtf8(context.secret("appKey"), request.firstHeader("X-App-Key"))) {
            return VerificationResult.failed("app key mismatch");
        }
        String timestamp = request.firstHeader("X-Timestamp");
        if (timestamp == null || timestamp.isBlank()) {
            return VerificationResult.failed("missing request timestamp");
        }
        try {
            long epochSeconds = Long.parseLong(timestamp);
            long now = Instant.now().getEpochSecond();
            if (epochSeconds < now - 300 || epochSeconds > now + 300) {
                return VerificationResult.failed("request timestamp is outside the 5 minute window");
            }
        } catch (NumberFormatException error) {
            return VerificationResult.failed("request timestamp must be epoch seconds");
        }
        String actual = request.firstHeader("X-Signature");
        String canonical = request.method() + "\n" + request.uri().getRawPath()
                + "\n" + timestamp + "\n" + request.bodyText();
        String expected = HmacSha256.signHex(context.secret("appSecret"), canonical);
        return ConstantTime.equalsUtf8(expected, actual) ? VerificationResult.ok()
                : VerificationResult.failed("request signature mismatch");
    }

    @Override
    public void signMockResponse(SignContext context, RequestSnapshot request, MutableResponse response) {
        response.header("X-Signature",
                HmacSha256.signHex(context.secret("appSecret"),
                        response.status() + "\n" + response.bodyText()));
    }
}
