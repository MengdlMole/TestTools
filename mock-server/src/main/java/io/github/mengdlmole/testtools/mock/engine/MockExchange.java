package io.github.mengdlmole.testtools.mock.engine;

import io.github.mengdlmole.testtools.http.transport.MutableResponse;
import io.github.mengdlmole.testtools.mock.callback.CallbackTask;
import java.util.List;

public record MockExchange(
    MutableResponse response, List<CallbackTask> callbacks, String matchedMock, long delayMs) {}
