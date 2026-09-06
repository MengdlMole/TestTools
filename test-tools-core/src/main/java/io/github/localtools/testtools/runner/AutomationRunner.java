package io.github.localtools.testtools.runner;

import io.github.localtools.testtools.runner.RunModels.CaseResult;
import io.github.localtools.testtools.runner.RunModels.ExecutionRecord;
import io.github.localtools.testtools.runner.RunModels.ErrorDetail;
import io.github.localtools.testtools.runner.RunModels.SuiteResult;
import io.github.localtools.testtools.workspace.WorkspaceModels.TestSuite;
import io.github.localtools.testtools.workspace.WorkspaceService;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AutomationRunner {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss-SSS");
    private final TestCaseRunner caseRunner;
    private final WorkspaceService workspace;

    public AutomationRunner(TestCaseRunner caseRunner, WorkspaceService workspace) {
        this.caseRunner = caseRunner;
        this.workspace = workspace;
    }

    public ExecutionRecord<CaseResult> runCase(String caseName) {
        CaseResult result;
        try {
            result = caseRunner.run(caseName);
        } catch (Exception error) {
            result = new CaseResult(caseName, null, false, 0, java.util.Map.of(), List.of(), ErrorDetail.from(error));
        }
        return persist("case-" + caseName, result);
    }

    public ExecutionRecord<SuiteResult> runSuite(String suiteName) {
        TestSuite suite = workspace.testSuite(suiteName);
        int repeat = suite.repeat() == null ? 1 : Math.max(1, Math.min(suite.repeat(), 100));
        int total = suite.cases().size() * repeat;
        boolean stopOnFailure = Boolean.TRUE.equals(suite.stopOnFailure());
        long started = System.nanoTime();
        List<ExecutionRecord<CaseResult>> results = new ArrayList<>();

        outer:
        for (int iteration = 0; iteration < repeat; iteration++) {
            for (String caseName : suite.cases()) {
                ExecutionRecord<CaseResult> result = runCase(caseName);
                results.add(result);
                if (!result.result().success() && stopOnFailure) break outer;
            }
        }
        return persistSuite(suiteName, suite.name(), total, started, results);
    }

    public ExecutionRecord<SuiteResult> runAll() {
        List<String> names = workspace.caseNames();
        TestSuite suite = new TestSuite("全部用例", names, false, 1);
        boolean success = true;
        long started = System.nanoTime();
        List<ExecutionRecord<CaseResult>> results = new ArrayList<>();
        for (String caseName : names) {
            ExecutionRecord<CaseResult> result = runCase(caseName);
            results.add(result);
            success &= result.result().success();
        }
        SuiteResult suiteResult = new SuiteResult(suite.name(), success, results.size(), names.size(),
                Duration.ofNanos(System.nanoTime() - started).toMillis(), List.copyOf(results));
        return persist("suite-all", suiteResult);
    }

    public ExecutionRecord<SuiteResult> persistSuite(String suiteFileName, String displayName, int total,
                                                      long startedNanos,
                                                      List<ExecutionRecord<CaseResult>> results) {
        boolean success = results.stream().allMatch(result -> result.result().success());
        SuiteResult suiteResult = new SuiteResult(displayName, success, results.size(), total,
                Duration.ofNanos(System.nanoTime() - startedNanos).toMillis(), List.copyOf(results));
        return persist("suite-" + suiteFileName, suiteResult);
    }

    private <T> ExecutionRecord<T> persist(String prefix, T result) {
        String runId = UUID.randomUUID().toString();
        String file = LocalDate.now() + "/" + TIME.format(LocalDateTime.now()) + "-" + prefix + "-" + runId + ".json";
        ExecutionRecord<T> record = new ExecutionRecord<>(runId, file, result);
        workspace.saveResult(file, record);
        return record;
    }
}
