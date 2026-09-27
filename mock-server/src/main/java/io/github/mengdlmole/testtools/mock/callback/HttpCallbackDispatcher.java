package io.github.mengdlmole.testtools.mock.callback;

import static io.github.mengdlmole.testtools.http.HttpHeaderSupport.putIfAbsentIgnoreCase;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mengdlmole.testtools.http.transport.HttpExecutor;
import io.github.mengdlmole.testtools.http.transport.MutableRequest;
import io.github.mengdlmole.testtools.mock.engine.HttpMockEngine;
import io.github.mengdlmole.testtools.mock.model.MockDefinition.AfterResponse;
import io.github.mengdlmole.testtools.mock.model.MockDefinition.CallbackRequest;
import io.github.mengdlmole.testtools.mock.model.MockDefinition.Retry;
import io.github.mengdlmole.testtools.security.HttpSecurityHandler;
import io.github.mengdlmole.testtools.security.SecurityHandlerRegistry;
import io.github.mengdlmole.testtools.security.SignContext;
import io.github.mengdlmole.testtools.security.VerificationResult;
import io.github.mengdlmole.testtools.workspace.TestWorkspace;
import io.github.mengdlmole.testtools.workspace.VariableResolver;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
public final class HttpCallbackDispatcher {
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss-SSS");
  private final TestWorkspace workspace;
  private final VariableResolver variables;
  private final SecurityHandlerRegistry handlers;
  private final HttpExecutor http;
  private final ThreadPoolTaskScheduler scheduler;
  private final Map<String, CallbackExecution> executions = new ConcurrentHashMap<>();
  private final ArrayDeque<String> recentIds = new ArrayDeque<>();

  public HttpCallbackDispatcher(
      TestWorkspace workspace,
      VariableResolver variables,
      SecurityHandlerRegistry handlers,
      HttpExecutor http,
      ThreadPoolTaskScheduler scheduler) {
    this.workspace = workspace;
    this.variables = variables;
    this.handlers = handlers;
    this.http = http;
    this.scheduler = scheduler;
  }

  public void submit(CallbackTask task) {
    AfterResponse definition = task.definition();
    String id = UUID.randomUUID().toString();
    String resultFile =
        "callbacks/"
            + LocalDate.now()
            + "/"
            + TIME.format(LocalDateTime.now())
            + "-"
            + safeName(definition.name())
            + "-"
            + id
            + ".json";
    CallbackExecution pending =
        new CallbackExecution(
            id,
            text(definition.name(), "callback"),
            task.mockName(),
            "PENDING",
            0,
            null,
            null,
            null,
            null,
            resultFile);
    put(pending);
    try {
      if (definition.request() == null) {
        throw new IllegalArgumentException("afterResponse.request is required");
      }
      long delay = definition.delayMs() == null ? 0 : Math.max(0, definition.delayMs());
      scheduler.schedule(() -> start(task, pending), Instant.now().plusMillis(delay));
    } catch (RuntimeException error) {
      complete(pending, "FAILED", 0, null, message(error));
    }
  }

  public synchronized List<CallbackExecution> recent() {
    return recentIds.stream().map(executions::get).filter(java.util.Objects::nonNull).toList();
  }

  private void start(CallbackTask task, CallbackExecution pending) {
    CallbackExecution running =
        new CallbackExecution(
            pending.id(),
            pending.name(),
            pending.mockName(),
            "RUNNING",
            0,
            null,
            null,
            Instant.now().toString(),
            null,
            pending.resultFile());
    executions.put(running.id(), running);

    Retry retry = task.definition().retry();
    int maxAttempts =
        retry == null || retry.maxAttempts() == null
            ? 1
            : Math.max(1, Math.min(retry.maxAttempts(), 10));
    long interval =
        retry == null || retry.intervalMs() == null ? 0 : Math.max(0, retry.intervalMs());
    long timeoutMs =
        task.definition().timeoutMs() == null ? 5000 : Math.max(1, task.definition().timeoutMs());
    attempt(task, running, 1, maxAttempts, interval, Duration.ofMillis(timeoutMs));
  }

  private void attempt(
      CallbackTask task,
      CallbackExecution running,
      int attempt,
      int maxAttempts,
      long intervalMs,
      Duration timeout) {
    Integer responseStatus = null;
    String errorMessage;
    try {
      AttemptResult result = invoke(task, running.id(), timeout);
      responseStatus = result.status();
      if (result.success()) {
        complete(running, "SUCCESS", attempt, responseStatus, null);
        return;
      }
      errorMessage = result.error();
    } catch (Exception error) {
      errorMessage = message(error);
    }

    if (attempt >= maxAttempts) {
      complete(running, "FAILED", attempt, responseStatus, errorMessage);
      return;
    }

    CallbackExecution waiting =
        new CallbackExecution(
            running.id(),
            running.name(),
            running.mockName(),
            "RETRY_WAIT",
            attempt,
            responseStatus,
            errorMessage,
            running.startedAt(),
            null,
            running.resultFile());
    executions.put(waiting.id(), waiting);
    try {
      scheduler.schedule(
          () -> attempt(task, waiting, attempt + 1, maxAttempts, intervalMs, timeout),
          Instant.now().plusMillis(intervalMs));
    } catch (RuntimeException error) {
      complete(
          waiting,
          "FAILED",
          attempt,
          responseStatus,
          "Cannot schedule callback retry: " + message(error));
    }
  }

  private AttemptResult invoke(CallbackTask task, String callbackId, Duration timeout)
      throws Exception {
    CallbackRequest definition = task.definition().request();
    Map<String, String> values =
        HttpMockEngine.requestVariables(task.originalRequest(), task.signContext().variables());
    values.put("mock.name", task.mockName());
    values.put("callback.id", callbackId);
    SignContext context = new SignContext(values, task.signContext().secrets());

    String url = variables.resolve(definition.url(), values);
    Map<String, String> headers = new LinkedHashMap<>();
    if (definition.headers() != null) {
      definition
          .headers()
          .forEach((key, value) -> headers.put(key, variables.resolve(value, values)));
    }
    byte[] body = callbackBody(definition, values);
    if (definition.body() != null) {
      putIfAbsentIgnoreCase(headers, "Content-Type", "application/json");
    }
    MutableRequest request =
        new MutableRequest(
            text(definition.method(), "POST").toUpperCase(), URI.create(url), headers, body);

    HttpSecurityHandler handler = handlers.byId(text(task.definition().securityHandler(), "none"));
    handler.signRequest(context, request);
    HttpExecutor.Exchange exchange = http.execute(request, timeout);
    VerificationResult verification =
        handler.verifyResponse(context, exchange.request(), exchange.response());
    boolean success =
        exchange.response().status() >= 200
            && exchange.response().status() < 300
            && verification.success();
    String error =
        success ? null : "HTTP " + exchange.response().status() + ", " + verification.message();
    return new AttemptResult(success, exchange.response().status(), error);
  }

  private byte[] callbackBody(CallbackRequest request, Map<String, String> values)
      throws Exception {
    if (request.bodyFile() != null) {
      return variables
          .resolve(
              new String(workspace.fileBytes(request.bodyFile()), StandardCharsets.UTF_8), values)
          .getBytes(StandardCharsets.UTF_8);
    }
    JsonNode body = variables.resolve(request.body(), values);
    return body == null ? new byte[0] : workspace.jsonMapper().writeValueAsBytes(body);
  }

  private void complete(
      CallbackExecution running,
      String status,
      int attempts,
      Integer responseStatus,
      String error) {
    CallbackExecution completed =
        new CallbackExecution(
            running.id(),
            running.name(),
            running.mockName(),
            status,
            attempts,
            responseStatus,
            error,
            running.startedAt(),
            Instant.now().toString(),
            running.resultFile());
    executions.put(completed.id(), completed);
    workspace.writeResult(completed.resultFile(), completed);
  }

  private synchronized void put(CallbackExecution execution) {
    executions.put(execution.id(), execution);
    recentIds.addFirst(execution.id());
    while (recentIds.size() > 100) {
      executions.remove(recentIds.removeLast());
    }
  }

  private String text(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String message(Throwable error) {
    return error.getMessage() == null ? error.getClass().getName() : error.getMessage();
  }

  private String safeName(String value) {
    String safe = text(value, "callback").replaceAll("[^a-zA-Z0-9_-]", "-");
    return safe.length() > 50 ? safe.substring(0, 50) : safe;
  }

  private record AttemptResult(boolean success, int status, String error) {}
}
