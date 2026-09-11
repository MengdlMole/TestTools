package io.github.mengdlmole.testtools.mock;

import io.github.mengdlmole.testtools.http.RequestSnapshot;
import io.github.mengdlmole.testtools.mock.model.MockDefinition.AfterResponse;
import io.github.mengdlmole.testtools.security.SignContext;

record CallbackTask(String mockName, AfterResponse definition,
                    RequestSnapshot originalRequest, SignContext signContext) {}
