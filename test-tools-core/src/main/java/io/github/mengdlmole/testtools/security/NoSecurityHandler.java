package io.github.mengdlmole.testtools.security;

import io.github.mengdlmole.testtools.http.transport.RequestSnapshot;

public class NoSecurityHandler implements HttpSecurityHandler {
  @Override
  public String id() {
    return "none";
  }

  @Override
  public VerificationResult verifyMockRequest(SignContext context, RequestSnapshot request) {
    return VerificationResult.ok();
  }
}
