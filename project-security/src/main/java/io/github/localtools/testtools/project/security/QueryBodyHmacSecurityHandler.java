package io.github.localtools.testtools.project.security;

import io.github.localtools.testtools.http.MutableRequest;
import io.github.localtools.testtools.http.MutableResponse;
import io.github.localtools.testtools.http.RequestSnapshot;
import io.github.localtools.testtools.http.ResponseSnapshot;
import io.github.localtools.testtools.security.HttpSecurityHandler;
import io.github.localtools.testtools.security.SignContext;
import io.github.localtools.testtools.security.VerificationResult;
import io.github.localtools.testtools.security.crypto.HmacSha256;

import java.net.URI;
import io.github.localtools.testtools.security.crypto.ConstantTime;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Example project protocol: sorted encoded query pairs plus the exact body text. */
public final class QueryBodyHmacSecurityHandler implements HttpSecurityHandler {
    @Override public String id() { return "queryBodyHmacSha256"; }

    @Override
    public void signRequest(SignContext context, MutableRequest request) {
        request.header("X-App-Key", context.secret("appKey"));
        request.header("X-Signature", signature(context, request.uri(), request.bodyText()));
    }

    @Override
    public VerificationResult verifyMockRequest(SignContext context, RequestSnapshot request) {
        String expected = signature(context, request.uri(), request.bodyText());
        String actual = request.firstHeader("X-Signature");
        return secureEquals(expected, actual) ? VerificationResult.ok()
                : VerificationResult.failed("request signature mismatch");
    }

    @Override
    public void signMockResponse(SignContext context, RequestSnapshot request, MutableResponse response) {
        response.header("X-Signature", HmacSha256.signHex(context.secret("appSecret"), response.bodyText()));
    }

    @Override
    public VerificationResult verifyResponse(SignContext context, RequestSnapshot request,
                                             ResponseSnapshot response) {
        String expected = HmacSha256.signHex(context.secret("appSecret"), response.bodyText());
        String actual = response.firstHeader("X-Signature");
        return secureEquals(expected, actual) ? VerificationResult.ok()
                : VerificationResult.failed("response signature mismatch");
    }

    static String buildSignData(URI uri, String bodyString) {
        String rawQuery = uri.getRawQuery();
        String queryString = rawQuery == null || rawQuery.isBlank() ? ""
                : Arrays.stream(rawQuery.split("&")).sorted().collect(Collectors.joining("&"));
        return queryString + (bodyString == null ? "" : bodyString);
    }

    private String signature(SignContext context, URI uri, String bodyString) {
        return HmacSha256.signHex(context.secret("appSecret"), buildSignData(uri, bodyString));
    }

    private boolean secureEquals(String expected, String actual) {
        return ConstantTime.equalsUtf8(expected, actual);
    }
}
