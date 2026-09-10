package io.github.localtools.testtools.mock;

import io.github.localtools.testtools.http.RequestSnapshot;
import io.github.localtools.testtools.mock.model.MockDefinition.AfterResponse;
import io.github.localtools.testtools.security.SignContext;

record CallbackTask(String mockName, AfterResponse definition,
                    RequestSnapshot originalRequest, SignContext signContext) {}
