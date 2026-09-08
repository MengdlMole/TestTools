package io.github.localtools.testtools.security;

import io.github.localtools.testtools.http.HttpModels.MutableRequest;
import io.github.localtools.testtools.http.HttpModels.MutableResponse;
import io.github.localtools.testtools.http.HttpModels.RequestSnapshot;
import io.github.localtools.testtools.http.HttpModels.ResponseSnapshot;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 * Runnable example protocol: sort encoded query pairs, append the exact body text,
 * then calculate a lowercase hex HMAC-SHA256 signature.
 */
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
        response.header("X-Signature", hmacSha256(context.secret("appSecret"), response.bodyText()));
    }

    @Override
    public VerificationResult verifyResponse(SignContext context, RequestSnapshot request, ResponseSnapshot response) {
        String expected = hmacSha256(context.secret("appSecret"), response.bodyText());
        String actual = response.firstHeader("X-Signature");
        return secureEquals(expected, actual) ? VerificationResult.ok()
                : VerificationResult.failed("response signature mismatch");
    }

    static String buildSignData(URI uri, String bodyString) {
        String rawQuery = uri.getRawQuery();
        String queryString = rawQuery == null || rawQuery.isBlank() ? ""
                : Arrays.stream(rawQuery.split("&"))
                        .sorted()
                        .collect(Collectors.joining("&"));
        return queryString + (bodyString == null ? "" : bodyString);
    }

    private String signature(SignContext context, URI uri, String bodyString) {
        return hmacSha256(context.secret("appSecret"), buildSignData(uri, bodyString));
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
