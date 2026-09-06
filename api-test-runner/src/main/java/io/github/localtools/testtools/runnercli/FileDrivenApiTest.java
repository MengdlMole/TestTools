package io.github.localtools.testtools.runnercli;

import io.github.localtools.testtools.TestToolsRuntime;
import io.github.localtools.testtools.runner.RunModels.CaseResult;
import io.github.localtools.testtools.runner.RunModels.ExecutionRecord;
import io.github.localtools.testtools.workspace.WorkspaceModels.TestSuite;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class FileDrivenApiTest {
    private final List<ExecutionRecord<CaseResult>> results = new ArrayList<>();
    private TestToolsRuntime runtime;
    private TestSuite suite;
    private boolean previousFailure;
    private int totalCases;
    private long suiteStarted;

    @DataProvider(name = "yamlCases")
    public Object[][] yamlCases() {
        runtime = TestToolsRuntime.open(RunnerSettings.workspace);
        List<CaseInvocation> cases = selectedCases();
        totalCases = cases.size();
        suiteStarted = System.nanoTime();
        return cases.stream().map(item -> new Object[]{item}).toArray(Object[][]::new);
    }

    @Test(dataProvider = "yamlCases")
    public void executeYamlCase(CaseInvocation invocation) {
        if (suite != null && Boolean.TRUE.equals(suite.stopOnFailure()) && previousFailure) {
            throw new SkipException("Skipped because stopOnFailure is enabled");
        }
        ExecutionRecord<CaseResult> execution = runtime.automationRunner().runCase(invocation.caseName());
        results.add(execution);
        CaseResult result = execution.result();
        previousFailure |= !result.success();
        Assert.assertTrue(result.success(), failureMessage(result, execution.resultFile()));
    }

    @AfterClass(alwaysRun = true)
    public void persistSuiteResult() {
        if (suite != null) {
            runtime.automationRunner().persistSuite(RunnerSettings.suiteName, suite.name(), totalCases,
                    suiteStarted, results);
        }
    }

    private List<CaseInvocation> selectedCases() {
        if (RunnerSettings.caseName != null) {
            return List.of(new CaseInvocation(RunnerSettings.caseName, RunnerSettings.caseName));
        }
        if (RunnerSettings.suiteName == null) {
            return runtime.workspace().caseNames().stream().map(name -> new CaseInvocation(name, name)).toList();
        }

        suite = runtime.workspace().testSuite(RunnerSettings.suiteName);
        int repeat = suite.repeat() == null ? 1 : Math.max(1, Math.min(suite.repeat(), 100));
        List<CaseInvocation> invocations = new ArrayList<>(suite.cases().size() * repeat);
        for (int iteration = 1; iteration <= repeat; iteration++) {
            for (String caseName : suite.cases()) {
                String display = repeat == 1 ? caseName : caseName + " [" + iteration + "/" + repeat + "]";
                invocations.add(new CaseInvocation(display, caseName));
            }
        }
        return invocations;
    }

    private String failureMessage(CaseResult result, String resultFile) {
        return "YAML case failed: " + result.name() + ", details: "
                + runtime.workspace().root().resolve("results").resolve(resultFile);
    }

    public record CaseInvocation(String displayName, String caseName) {
        @Override public String toString() { return displayName; }
    }
}
