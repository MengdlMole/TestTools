package io.github.localtools.testtools.mock;

import io.github.localtools.testtools.http.MutableResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HttpMockController {
    static final String CALLBACKS_ATTRIBUTE = HttpMockController.class.getName() + ".callbacks";
    private final HttpMockEngine engine;

    HttpMockController(HttpMockEngine engine) { this.engine = engine; }

    @RequestMapping("/**")
    ResponseEntity<byte[]> mock(HttpServletRequest request,
                                @RequestBody(required = false) byte[] body) {
        MockExchange exchange = engine.execute(request, body);
        request.setAttribute(CALLBACKS_ATTRIBUTE, exchange.callbacks());
        MutableResponse response = exchange.response();
        HttpHeaders headers = new HttpHeaders();
        response.headers().forEach(headers::set);
        return ResponseEntity.status(response.status()).headers(headers).body(response.body());
    }
}
