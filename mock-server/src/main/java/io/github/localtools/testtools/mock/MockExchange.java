package io.github.localtools.testtools.mock;

import io.github.localtools.testtools.http.MutableResponse;

import java.util.List;

record MockExchange(MutableResponse response, List<CallbackTask> callbacks) {}
