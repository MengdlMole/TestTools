package io.github.mengdlmole.testtools.apitest.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mengdlmole.testtools.apitest.support.ApiTestSupport;
import io.github.mengdlmole.testtools.http.ApiRequest;
import io.github.mengdlmole.testtools.http.ApiResponse;
import io.github.mengdlmole.testtools.http.ApiTestClient;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Global, case-owned, and variable-resolved JSON request bodies.
 */
public final class JsonFixtureExamples extends ApiTestSupport {
  private ApiTestClient client;

  @BeforeEach
  void setUp() {
    client = api("local");
  }

  @Test
  void sendsGlobalJsonVerbatim() {
    ApiRequest request = client.post("/examples/json");
    request.jsonBodyFile(globalFile("echo-request.json"));

    ApiResponse response = client.send(request);

    assertEquals(200, response.status());
  }

  @Test
  void readsAndModifiesGlobalJsonBeforeSending() {
    ObjectNode body = globalJsonObject("echo-request.json");
    body.put("message", "changed by this JUnit test");
    body.put("requestNumber", 2);

    ApiRequest request = client.post("/examples/json");
    request.jsonBody(body);

    ApiResponse response = client.send(request);

    assertEquals(200, response.status());
  }

  @Test
  void resolvesVariablesInGlobalJson() {
    JsonNode body =
        resolvedGlobalJson(
            "local", "echo-template.json", Map.of("clientName", "junit-json-template"));

    ApiRequest request = client.post("/examples/json");
    request.jsonBody(body);

    ApiResponse response = client.send(request);

    assertEquals(200, response.status());
  }

  @Test
  void readsAndModifiesCaseOwnedJson() {
    ObjectNode body = caseJsonObject("json-fixture-examples", "echo-request.json");
    body.put("tenantId", "tenant-overridden-by-test");

    ApiRequest request = client.post("/examples/json");
    request.jsonBody(body);

    ApiResponse response = client.send(request);

    assertEquals(200, response.status());
  }
}
