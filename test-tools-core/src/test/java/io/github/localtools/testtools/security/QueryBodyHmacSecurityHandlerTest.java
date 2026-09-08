package io.github.localtools.testtools.security;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryBodyHmacSecurityHandlerTest {
    @Test
    void usesEncodedQueryPairsInSortedOrderThenAppendsExactBody() {
        String actual = QueryBodyHmacSecurityHandler.buildSignData(
                URI.create("http://localhost/orders?z=last&name=a+b&name=a%2Bb"),
                "{\"quantity\":2}");

        assertEquals("name=a%2Bb&name=a+b&z=last{\"quantity\":2}", actual);
    }
}
