package io.github.localtools.testtools.mock;

import org.springframework.stereotype.Component;
import io.github.localtools.testtools.http.SensitiveDataMasker;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static io.github.localtools.testtools.mock.MockRuntimeModels.MockCall;

@Component
class MockCallStore {
    private final ArrayDeque<MockCall> calls = new ArrayDeque<>();

    synchronized void record(String method, String path, int status, String matchedMock) {
        calls.addFirst(new MockCall(Instant.now().toString(), method, SensitiveDataMasker.maskUri(path), status, matchedMock));
        while (calls.size() > 100) calls.removeLast();
    }

    synchronized List<MockCall> recent() { return new ArrayList<>(calls); }
}
