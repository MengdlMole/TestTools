package io.github.mengdlmole.testtools.mock.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mengdlmole.testtools.mock.model.MockDefinition;
import io.github.mengdlmole.testtools.mock.model.MockDefinition.AfterResponse;
import io.github.mengdlmole.testtools.mock.model.MockDefinition.CallbackRequest;
import io.github.mengdlmole.testtools.mock.model.MockDefinition.Request;
import io.github.mengdlmole.testtools.mock.model.MockDefinition.Response;
import io.github.mengdlmole.testtools.security.SignContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MockCatalogTest {
  @Test
  void definitionSnapshotsCannotMutateCatalogState() {
    ObjectNode requestBody = new ObjectMapper().createObjectNode().put("name", "original");
    Map<String, String> headers = new LinkedHashMap<>(Map.of("X-Mode", "original"));
    List<AfterResponse> afterResponse = new ArrayList<>();
    afterResponse.add(
        new AfterResponse(
            "notify",
            0L,
            1000L,
            null,
            null,
            new CallbackRequest("POST", "http://localhost/callback", headers, requestBody, null)));
    MockDefinition source =
        new MockDefinition(
            "immutable",
            true,
            1,
            new Request("POST", "/orders", Map.of(), headers, requestBody),
            new Response(200, headers, requestBody, null, 0L),
            null,
            afterResponse);
    MockCatalog catalog =
        new MockCatalog(
            1,
            Instant.EPOCH,
            "fingerprint",
            new SignContext(Map.of(), Map.of()),
            List.of(new MockCatalog.LoadedMock(source, null, List.of())));

    headers.put("X-Mode", "changed");
    requestBody.put("name", "changed");
    afterResponse.clear();
    MockDefinition first = catalog.definitions().getFirst().definition();
    ((ObjectNode) first.request().body()).put("name", "caller-change");

    MockDefinition second = catalog.definitions().getFirst().definition();
    assertEquals("original", second.request().headers().get("X-Mode"));
    assertEquals("original", second.request().body().path("name").asText());
    assertEquals(1, second.afterResponse().size());
    assertThrows(
        UnsupportedOperationException.class,
        () -> second.response().headers().put("X-Other", "value"));
  }
}
