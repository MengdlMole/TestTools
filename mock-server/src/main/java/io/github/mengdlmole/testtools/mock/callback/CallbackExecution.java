package io.github.mengdlmole.testtools.mock.callback;

public record CallbackExecution(
    String id,
    String name,
    String mockName,
    String status,
    int attempts,
    Integer responseStatus,
    String error,
    String startedAt,
    String completedAt,
    String resultFile) {}
