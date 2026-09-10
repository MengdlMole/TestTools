package io.github.localtools.testtools.yamlrunner.result;

import java.util.List;

public record SuiteResult(String name, boolean success, int executedCases,
                          int totalCases, long durationMs,
                          List<ExecutionRecord<CaseResult>> cases) {}
