package io.github.mengdlmole.testtools.mock.web;

import static io.github.mengdlmole.testtools.mock.callback.CallbackCompletionInterceptor.CALLBACKS_ATTRIBUTE;

import io.github.mengdlmole.testtools.http.transport.MutableResponse;
import io.github.mengdlmole.testtools.http.transport.RequestSnapshot;
import io.github.mengdlmole.testtools.mock.admin.MockCallStore;
import io.github.mengdlmole.testtools.mock.engine.HttpMockEngine;
import io.github.mengdlmole.testtools.mock.engine.MockExchange;
import io.github.mengdlmole.testtools.mock.engine.MockRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
final class HttpMockController {
  private final HttpMockEngine engine;
  private final MockCallStore calls;

  HttpMockController(HttpMockEngine engine, MockCallStore calls) {
    this.engine = engine;
    this.calls = calls;
  }

  @RequestMapping("/**")
  ResponseEntity<byte[]> mock(
      HttpServletRequest request, @RequestBody(required = false) byte[] body) {
    MockRequest incoming = adapt(request, body);
    MockExchange exchange = engine.execute(incoming);
    delay(exchange.delayMs());
    MutableResponse response = exchange.response();
    HttpHeaders headers = new HttpHeaders();
    response.headers().forEach(headers::set);
    ResponseEntity<byte[]> result =
        ResponseEntity.status(response.status()).headers(headers).body(response.body());
    calls.record(
        incoming.snapshot().method(),
        incoming.snapshot().uri().toString(),
        response.status(),
        exchange.matchedMock());
    request.setAttribute(CALLBACKS_ATTRIBUTE, exchange.callbacks());
    return result;
  }

  private MockRequest adapt(HttpServletRequest request, byte[] body) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    var names = request.getHeaderNames();
    if (names != null) {
      while (names.hasMoreElements()) {
        String name = names.nextElement();
        headers.put(name, Collections.list(request.getHeaders(name)));
      }
    }
    Map<String, List<String>> query = new LinkedHashMap<>();
    request.getParameterMap().forEach((name, values) -> query.put(name, Arrays.asList(values)));
    String uri = request.getRequestURI();
    if (request.getQueryString() != null) {
      uri += "?" + request.getQueryString();
    }
    RequestSnapshot snapshot =
        new RequestSnapshot(
            request.getMethod(), URI.create(uri), headers, body == null ? new byte[0] : body);
    return new MockRequest(snapshot, query);
  }

  private void delay(long delayMs) {
    if (delayMs <= 0) {
      return;
    }
    try {
      Thread.sleep(delayMs);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Mock response delay interrupted", error);
    }
  }
}
