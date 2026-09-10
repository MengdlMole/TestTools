package io.github.localtools.testtools.security;

import io.github.localtools.testtools.http.MutableRequest;
import io.github.localtools.testtools.http.MutableResponse;
import io.github.localtools.testtools.http.RequestSnapshot;
import io.github.localtools.testtools.http.ResponseSnapshot;

public interface HttpSecurityHandler {
    String id();
    default void signRequest(SignContext context, MutableRequest request) {}
    default VerificationResult verifyResponse(SignContext context, RequestSnapshot request, ResponseSnapshot response) {
        return VerificationResult.ok();
    }
    default VerificationResult verifyMockRequest(SignContext context, RequestSnapshot request) {
        return VerificationResult.ok();
    }
    default void signMockResponse(SignContext context, RequestSnapshot request, MutableResponse response) {}
}
