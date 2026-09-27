package io.github.mengdlmole.testtools.mock.callback;

import io.github.mengdlmole.testtools.http.transport.RequestSnapshot;
import io.github.mengdlmole.testtools.mock.model.MockDefinition.AfterResponse;
import io.github.mengdlmole.testtools.security.SignContext;

public record CallbackTask(
    String mockName,
    AfterResponse definition,
    byte[] bodyFile,
    RequestSnapshot originalRequest,
    SignContext signContext) {
  public CallbackTask {
    bodyFile = bodyFile == null ? null : bodyFile.clone();
  }

  @Override
  public byte[] bodyFile() {
    return bodyFile == null ? null : bodyFile.clone();
  }
}
