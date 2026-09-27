package io.github.mengdlmole.testtools.security;

import io.github.mengdlmole.testtools.http.transport.MutableRequest;
import io.github.mengdlmole.testtools.http.transport.MutableResponse;
import io.github.mengdlmole.testtools.http.transport.RequestSnapshot;
import io.github.mengdlmole.testtools.http.transport.ResponseSnapshot;

/**
 * One concrete HTTP authentication protocol used by Mock endpoints and their callbacks.
 *
 * <p>Every handler must explicitly implement inbound request verification. This prevents an
 * accidentally selected handler from silently accepting an unsigned Mock request. Optional
 * outbound and response operations remain no-ops because not every protocol signs both
 * directions.
 *
 * @since 0.2.0
 */
public interface HttpSecurityHandler {
  /**
   * Returns the identifier referenced by a Mock YAML definition.
   *
   * @return unique handler identifier
   */
  String id();

  /**
   * Adds authentication data to an outbound Mock callback.
   *
   * @param context resolved variables and secrets
   * @param request callback request to update
   */
  default void signRequest(SignContext context, MutableRequest request) {}

  /**
   * Verifies the response received by an outbound Mock callback.
   *
   * @param context resolved variables and secrets
   * @param request sent callback request
   * @param response received callback response
   * @return verification result
   */
  default VerificationResult verifyResponse(
      SignContext context, RequestSnapshot request, ResponseSnapshot response) {
    return VerificationResult.ok();
  }

  /**
   * Verifies an inbound request before a Mock response is created.
   *
   * @param context resolved variables and secrets
   * @param request received Mock request
   * @return verification result
   */
  VerificationResult verifyMockRequest(SignContext context, RequestSnapshot request);

  /**
   * Adds authentication data to a Mock response.
   *
   * @param context resolved variables and secrets
   * @param request received Mock request
   * @param response Mock response to update
   */
  default void signMockResponse(
      SignContext context, RequestSnapshot request, MutableResponse response) {}
}
