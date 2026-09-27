package io.github.mengdlmole.testtools.mock.callback;

import io.github.mengdlmole.testtools.http.transport.RequestSnapshot;
import io.github.mengdlmole.testtools.mock.model.MockDefinition.AfterResponse;
import io.github.mengdlmole.testtools.security.SignContext;

public record CallbackTask(
    String mockName,
    AfterResponse definition,
    RequestSnapshot originalRequest,
    SignContext signContext) {}
