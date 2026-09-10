package io.github.localtools.testtools.apitest;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.localtools.testtools.http.ApiTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Global, case-owned, and variable-resolved JSON request bodies. */
public final class JsonFixtureExamples extends ApiTestSupport {
    private ApiTestClient client;

    @BeforeEach
    void setUp() { client = api("local"); }

    @Test
    void sendsGlobalJsonVerbatim() {
        ApiTestClient.ApiResponse response = client.post("/signed/echo")
                .jsonBodyFile(globalJson("echo-request.json"))
                .security(securityHandler("demoHmacSha256"), signContext("local"))
                .executeVerified();
        assertEquals(200, response.status());
    }

    @Test
    void resolvesVariablesInGlobalJson() {
        JsonNode body = resolvedGlobalJson(
                "local", "echo-template.json", Map.of("clientName", "junit-json-template"));
        ApiTestClient.ApiResponse response = client.post("/signed/echo")
                .jsonBody(body)
                .security(securityHandler("demoHmacSha256"), signContext("local"))
                .executeVerified();
        assertEquals(200, response.status());
    }

    @Test
    void sendsCaseOwnedJsonVerbatim() {
        ApiTestClient.ApiResponse response = client.post("/signed/echo")
                .jsonBodyFile(caseJson("code-first-json", "echo-request.json"))
                .security(securityHandler("demoHmacSha256"), signContext("local"))
                .executeVerified();
        assertEquals(200, response.status());
    }
}
