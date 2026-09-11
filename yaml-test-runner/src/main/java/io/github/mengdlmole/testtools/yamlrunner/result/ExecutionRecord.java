package io.github.mengdlmole.testtools.yamlrunner.result;

public record ExecutionRecord<T>(String runId, String resultFile, T result) {}
