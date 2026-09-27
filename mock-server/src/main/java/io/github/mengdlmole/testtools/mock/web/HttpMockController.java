package io.github.mengdlmole.testtools.mock.web;

import static io.github.mengdlmole.testtools.mock.callback.CallbackCompletionInterceptor.CALLBACKS_ATTRIBUTE;

import io.github.mengdlmole.testtools.http.transport.MutableResponse;
import io.github.mengdlmole.testtools.mock.engine.HttpMockEngine;
import io.github.mengdlmole.testtools.mock.engine.MockExchange;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
final class HttpMockController {
  private final HttpMockEngine engine;

  HttpMockController(HttpMockEngine engine) {
    this.engine = engine;
  }

  @RequestMapping("/**")
  ResponseEntity<byte[]> mock(
      HttpServletRequest request, @RequestBody(required = false) byte[] body) {
    MockExchange exchange = engine.execute(request, body);
    request.setAttribute(CALLBACKS_ATTRIBUTE, exchange.callbacks());
    MutableResponse response = exchange.response();
    HttpHeaders headers = new HttpHeaders();
    response.headers().forEach(headers::set);
    return ResponseEntity.status(response.status()).headers(headers).body(response.body());
  }
}
