package io.github.mengdlmole.testtools.mock.callback;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mengdlmole.testtools.http.transport.HttpExecutor;
import io.github.mengdlmole.testtools.http.transport.MutableRequest;
import io.github.mengdlmole.testtools.http.transport.RequestSnapshot;
import io.github.mengdlmole.testtools.http.transport.ResponseSnapshot;
import io.github.mengdlmole.testtools.mock.model.MockDefinition.AfterResponse;
import io.github.mengdlmole.testtools.mock.model.MockDefinition.CallbackRequest;
import io.github.mengdlmole.testtools.mock.model.MockDefinition.Retry;
import io.github.mengdlmole.testtools.security.NoSecurityHandler;
import io.github.mengdlmole.testtools.security.SecurityHandlerRegistry;
import io.github.mengdlmole.testtools.security.SignContext;
import io.github.mengdlmole.testtools.workspace.TestWorkspace;
import io.github.mengdlmole.testtools.workspace.VariableResolver;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class HttpCallbackDispatcherTest {
  @TempDir java.nio.file.Path workspaceRoot;

  @Test
  void retryDelayDoesNotBlockOtherCallbacks() throws Exception {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setWaitForTasksToCompleteOnShutdown(false);
    scheduler.initialize();
    CallbackExecutor http = new CallbackExecutor();
    TestWorkspace workspace = new TestWorkspace(workspaceRoot);
    HttpCallbackDispatcher dispatcher =
        new HttpCallbackDispatcher(
            workspace,
            new VariableResolver(workspace.jsonMapper()),
            new SecurityHandlerRegistry(List.of(new NoSecurityHandler())),
            http,
            scheduler);

    try {
      dispatcher.submit(task("retry", new Retry(2, 5000L)));
      assertTrue(http.retryAttempt.await(1, TimeUnit.SECONDS));

      dispatcher.submit(task("quick", null));

      assertTrue(
          http.quickAttempt.await(1, TimeUnit.SECONDS),
          "a retry interval must not occupy the only scheduler thread");
      assertTrue(awaitCompleted(dispatcher, "quick"));
    } finally {
      scheduler.shutdown();
    }
  }

  private boolean awaitCompleted(HttpCallbackDispatcher dispatcher, String name)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
    while (System.nanoTime() < deadline) {
      boolean persisted =
          dispatcher.recent().stream()
              .filter(item -> name.equals(item.name()) && "SUCCESS".equals(item.status()))
              .anyMatch(
                  item ->
                      java.nio.file.Files.isRegularFile(
                          workspaceRoot.resolve("results").resolve(item.resultFile())));
      if (persisted) {
        return true;
      }
      Thread.sleep(10);
    }
    return false;
  }

  private CallbackTask task(String path, Retry retry) {
    AfterResponse definition =
        new AfterResponse(
            path,
            0L,
            100L,
            "none",
            retry,
            new CallbackRequest("POST", "http://localhost/" + path, Map.of(), null, null));
    RequestSnapshot original =
        new RequestSnapshot("POST", URI.create("/original"), Map.of(), new byte[0]);
    return new CallbackTask(
        "source-mock", definition, null, original, new SignContext(Map.of(), Map.of()));
  }

  private static final class CallbackExecutor extends HttpExecutor {
    private final CountDownLatch retryAttempt = new CountDownLatch(1);
    private final CountDownLatch quickAttempt = new CountDownLatch(1);

    @Override
    public Exchange execute(MutableRequest request, Duration timeout) {
      boolean retry = "/retry".equals(request.uri().getPath());
      if (retry) {
        retryAttempt.countDown();
      } else {
        quickAttempt.countDown();
      }
      RequestSnapshot sent =
          new RequestSnapshot(request.method(), request.uri(), Map.of(), request.body());
      ResponseSnapshot response = new ResponseSnapshot(retry ? 500 : 204, Map.of(), new byte[0], 1);
      return new Exchange(sent, response);
    }
  }
}
