package io.github.mengdlmole.testtools.mock;

import org.springframework.stereotype.Component;
import io.github.mengdlmole.testtools.http.SensitiveDataMasker;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

@Component
class MockCallStore {
    private final ArrayDeque<MockCall> calls = new ArrayDeque<>();

    synchronized void record(String method, String path, int status, String matchedMock) {
        calls.addFirst(new MockCall(Instant.now().toString(), method, SensitiveDataMasker.maskUri(path), status, matchedMock));
        while (calls.size() > 100) calls.removeLast();
    }

    synchronized List<MockCall> recent() { return new ArrayList<>(calls); }
}
