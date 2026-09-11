package io.github.mengdlmole.testtools.mock;

import io.github.mengdlmole.testtools.http.MutableResponse;

import java.util.List;

record MockExchange(MutableResponse response, List<CallbackTask> callbacks) {}
