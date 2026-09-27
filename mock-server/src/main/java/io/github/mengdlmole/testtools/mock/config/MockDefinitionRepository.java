package io.github.mengdlmole.testtools.mock.config;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Provides an atomically replaceable, last-known-good Mock catalog.
 */
@Component
public final class MockDefinitionRepository {
  private final MockWorkspace workspace;
  private final long reloadIntervalNanos;
  private final AtomicReference<MockCatalog> current;
  private volatile long nextReloadCheck;
  private volatile Instant lastCheckedAt;
  private volatile String lastError;

  public MockDefinitionRepository(
      MockWorkspace workspace,
      @Value("${test-tools.mock-reload-interval-ms:500}") long reloadIntervalMs) {
    this.workspace = workspace;
    this.reloadIntervalNanos = Math.max(0, reloadIntervalMs) * 1_000_000L;
    this.current = new AtomicReference<>(workspace.loadCatalog(1));
    this.lastCheckedAt = Instant.now();
    this.nextReloadCheck = System.nanoTime() + reloadIntervalNanos;
  }

  public MockCatalog current() {
    reloadIfDue();
    return current.get();
  }

  public synchronized MockCatalog reloadNow() {
    MockCatalog previous = current.get();
    lastCheckedAt = Instant.now();
    try {
      MockCatalog candidate = workspace.loadCatalog(previous.version() + 1);
      lastError = null;
      if (!candidate.fingerprint().equals(previous.fingerprint())) {
        current.set(candidate);
        return candidate;
      }
      return previous;
    } catch (RuntimeException error) {
      lastError = error.getMessage() == null ? error.getClass().getName() : error.getMessage();
      return previous;
    } finally {
      nextReloadCheck = System.nanoTime() + reloadIntervalNanos;
    }
  }

  public MockCatalogStatus status() {
    MockCatalog catalog = current();
    return new MockCatalogStatus(
        catalog.version(),
        catalog.loadedAt().toString(),
        catalog.definitions().size(),
        lastCheckedAt.toString(),
        lastError);
  }

  private void reloadIfDue() {
    if (System.nanoTime() < nextReloadCheck) {
      return;
    }
    synchronized (this) {
      if (System.nanoTime() >= nextReloadCheck) {
        reloadNow();
      }
    }
  }
}
