package io.github.mengdlmole.testtools.http;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpSnapshotTest {
    @Test
    void snapshotsDefensivelyCopyHeadersAndBodies() {
        byte[] body = {1, 2};
        List<String> values = new ArrayList<>(List.of("one"));
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("X-Test", values);
        RequestSnapshot snapshot = new RequestSnapshot("POST", URI.create("http://localhost"), headers, body);

        body[0] = 9;
        values.add("two");
        snapshot.body()[1] = 9;

        assertArrayEquals(new byte[]{1, 2}, snapshot.body());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.headers().put("X-Other", List.of("value")));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.headers().get("X-Test").add("three"));
    }
}
