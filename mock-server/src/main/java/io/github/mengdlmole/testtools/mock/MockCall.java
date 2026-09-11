package io.github.mengdlmole.testtools.mock;

record MockCall(String time, String method, String path, int responseStatus, String matchedMock) {}
