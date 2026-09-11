package io.github.mengdlmole.testtools.yamlrunner.result;

import java.util.List;
import java.util.Map;

public record CaseResult(String name, String environment, boolean success,
                         long durationMs, Map<String, String> variables,
                         List<StepResult> steps, ErrorDetail error) {}
