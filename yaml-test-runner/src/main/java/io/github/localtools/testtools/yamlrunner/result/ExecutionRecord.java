package io.github.localtools.testtools.yamlrunner.result;

public record ExecutionRecord<T>(String runId, String resultFile, T result) {}
