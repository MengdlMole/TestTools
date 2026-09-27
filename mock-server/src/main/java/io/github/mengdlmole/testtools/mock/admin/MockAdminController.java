package io.github.mengdlmole.testtools.mock.admin;

import io.github.mengdlmole.testtools.mock.callback.CallbackExecution;
import io.github.mengdlmole.testtools.mock.callback.HttpCallbackDispatcher;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/__testtools")
final class MockAdminController {
  private final MockCallStore calls;
  private final HttpCallbackDispatcher callbacks;

  MockAdminController(MockCallStore calls, HttpCallbackDispatcher callbacks) {
    this.calls = calls;
    this.callbacks = callbacks;
  }

  @GetMapping("/health")
  Map<String, String> health() {
    return Map.of("status", "UP");
  }

  @GetMapping("/calls")
  List<MockCall> calls() {
    return calls.recent();
  }

  @GetMapping("/callbacks")
  List<CallbackExecution> callbacks() {
    return callbacks.recent();
  }
}
