package io.github.localtools.testtools.yamlrunner;

import io.github.localtools.testtools.yamlrunner.engine.YamlTestRuntime;
import io.github.localtools.testtools.yamlrunner.result.AssertionResult;
import io.github.localtools.testtools.yamlrunner.result.CaseResult;
import io.github.localtools.testtools.yamlrunner.result.ExecutionRecord;
import io.github.localtools.testtools.yamlrunner.result.StepResult;
import org.testng.Assert;
import org.testng.Reporter;

/** Base class for one-click Run/Debug of an individual YAML case from an IDE. */
public abstract class YamlCaseTestSupport {
    private YamlTestRuntime runtime;

    protected final void runCase(String caseName) {
        log("\n========== API CASE START: " + caseName + " ==========");
        log("workspace: " + runtime().workspace().files().root());

        ExecutionRecord<CaseResult> execution = runtime().suiteRunner().runCase(caseName);
        CaseResult result = execution.result();
        log("case: " + text(result.name(), caseName) + ", environment: "
                + text(result.environment(), "unknown") + ", duration: " + result.durationMs() + " ms");

        if (result.error() != null) {
            log("case error: " + result.error().type() + ": " + result.error().message());
        }
        for (int index = 0; index < result.steps().size(); index++) {
            logStep(index + 1, result.steps().get(index));
        }

        String resultPath = runtime().workspace().files().root().resolve("results").resolve(execution.resultFile()).toString();
        log("result: " + resultPath);
        log("========== API CASE " + (result.success() ? "PASSED" : "FAILED") + ": " + caseName
                + " ==========\n");
        Assert.assertTrue(result.success(), "YAML case failed: " + caseName + ", details: " + resultPath);
    }

    private YamlTestRuntime runtime() {
        if (runtime == null) runtime = YamlTestRuntime.open(YamlRunSelection.workspace);
        return runtime;
    }

    private void logStep(int index, StepResult step) {
        log("  [step " + index + "] " + step.name() + " -> " + (step.success() ? "PASSED" : "FAILED"));
        log("    request : " + text(step.method(), "") + " " + text(step.url(), ""));
        if (!step.requestHeaders().isEmpty()) log("    headers : " + step.requestHeaders());
        if (!step.requestBody().isBlank()) log("    body    : " + body(step.requestBody()));
        log("    response: HTTP " + step.responseStatus() + " (" + step.durationMs() + " ms)");
        if (!step.responseHeaders().isEmpty()) log("    headers : " + step.responseHeaders());
        if (!step.responseBody().isBlank()) log("    body    : " + body(step.responseBody()));
        log("    security: " + text(step.securityHandler(), "none") + ", " + text(step.verification(), ""));
        for (AssertionResult assertion : step.assertions()) {
            log("    assert  : " + assertion.type() + " -> "
                    + (assertion.success() ? "PASSED" : "FAILED") + " (" + assertion.message() + ")");
        }
        if (step.error() != null) {
            log("    error   : " + step.error().type() + ": " + step.error().message());
        }
    }

    private String body(String value) {
        int limit = Integer.getInteger("testtools.logBodyLimit", 4000);
        if (limit < 0 || value.length() <= limit) return value;
        return value.substring(0, limit) + "... [truncated, total " + value.length() + " chars]";
    }

    private String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void log(String message) {
        Reporter.log(message, true);
    }
}
