package io.github.localtools.testtools.mock;

import io.github.localtools.testtools.http.HttpModels.MutableResponse;
import io.github.localtools.testtools.http.HttpModels.RequestSnapshot;
import io.github.localtools.testtools.security.SignContext;
import io.github.localtools.testtools.workspace.WorkspaceModels.AfterResponseDefinition;

import java.util.List;

final class MockRuntimeModels {
    private MockRuntimeModels() {}

    record MockExchange(MutableResponse response, List<CallbackTask> callbacks) {}
    record CallbackTask(String mockName, AfterResponseDefinition definition,
                        RequestSnapshot originalRequest, SignContext signContext) {}
    record MockCall(String time, String method, String path, int responseStatus, String matchedMock) {}
    record CallbackExecution(String id, String name, String mockName, String status,
                             int attempts, Integer responseStatus, String error,
                             String startedAt, String completedAt, String resultFile) {}
}
