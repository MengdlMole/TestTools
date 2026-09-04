package io.github.localtools.testtools.security;

import io.github.localtools.testtools.http.HttpModels.MutableRequest;
import io.github.localtools.testtools.http.HttpModels.MutableResponse;
import io.github.localtools.testtools.http.HttpModels.RequestSnapshot;
import io.github.localtools.testtools.http.HttpModels.ResponseSnapshot;

public interface ApiSecurityHandler {
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
