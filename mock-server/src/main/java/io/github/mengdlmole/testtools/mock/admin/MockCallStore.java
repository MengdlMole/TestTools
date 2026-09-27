package io.github.mengdlmole.testtools.mock.admin;

import io.github.mengdlmole.testtools.http.SensitiveDataMasker;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class MockCallStore {
  private final ArrayDeque<MockCall> calls = new ArrayDeque<>();
  private final boolean maskSensitiveData;

  public MockCallStore(@Value("${test-tools.mask-sensitive-data:true}") boolean maskSensitiveData) {
    this.maskSensitiveData = maskSensitiveData;
  }

  public synchronized void record(String method, String path, int status, String matchedMock) {
    calls.addFirst(
        new MockCall(
            Instant.now().toString(),
            method,
            maskSensitiveData ? SensitiveDataMasker.maskUri(path) : path,
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
