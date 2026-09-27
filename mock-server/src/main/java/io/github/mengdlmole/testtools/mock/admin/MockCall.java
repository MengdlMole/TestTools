package io.github.mengdlmole.testtools.mock.admin;

public record MockCall(
    String time, String method, String path, int responseStatus, String matchedMock) {}
