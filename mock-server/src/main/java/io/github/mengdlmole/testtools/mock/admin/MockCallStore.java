package io.github.mengdlmole.testtools.mock.admin;

import io.github.mengdlmole.testtools.http.SensitiveDataMasker;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class MockCallStore {
  private final ArrayDeque<MockCall> calls = new ArrayDeque<>();

  public synchronized void record(String method, String path, int status, String matchedMock) {
    calls.addFirst(
        new MockCall(
            Instant.now().toString(),
            method,
            SensitiveDataMasker.maskUri(path),
            status,
            matchedMock));
    while (calls.size() > 100) {
      calls.removeLast();
    }
  }

  public synchronized List<MockCall> recent() {
    return new ArrayList<>(calls);
  }
}
