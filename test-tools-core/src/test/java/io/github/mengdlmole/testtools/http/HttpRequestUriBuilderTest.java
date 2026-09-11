package io.github.mengdlmole.testtools.http;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpRequestUriBuilderTest {
    @Test
    void appendsEncodedQueryToExistingQueryBeforeFragment() {
        URI uri = HttpRequestUriBuilder.build("http://localhost:8080/", "/orders?active=true#result", List.of(
                new HttpRequestUriBuilder.QueryParameter("name", "hello world"),
                new HttpRequestUriBuilder.QueryParameter("name", "again")));

        assertEquals("http://localhost:8080/orders?active=true&name=hello+world&name=again#result", uri.toString());
    }

    @Test
    void keepsAbsoluteHttpUrlIndependentOfBaseUrl() {
        URI uri = HttpRequestUriBuilder.build("http://localhost:8080", "HTTPS://example.test/health", List.of());

        assertEquals("HTTPS://example.test/health", uri.toString());
    }
}
