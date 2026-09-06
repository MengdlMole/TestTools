package io.github.localtools.testtools.mock;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
class MockErrorHandler {
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> handle(Exception error) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Mock execution failed",
                "message", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
    }
}
