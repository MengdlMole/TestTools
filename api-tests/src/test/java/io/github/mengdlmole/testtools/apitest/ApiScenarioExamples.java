package io.github.mengdlmole.testtools.apitest;

import io.github.mengdlmole.testtools.http.ApiTestClient;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Run this JUnit method to execute and optionally repeat the whole related API flow. */
public final class ApiScenarioExamples extends ApiTestSupport {
    private ApiTestClient client;

    @Test
    void newFeatureScenario() {
        scenario("新需求关联接口验证")
                .repeat(Integer.getInteger("testtools.repeat", 1))
                .beforeEach(context -> {
                    client = api("local");
                    context.put("tenantId", "local-tenant");
                })
                .step("确认服务可用", context -> {
                    ApiTestClient.ApiResponse response = client.get("/health").execute();
                    assertEquals(200, response.status());
                })
                .step("调用签名接口", context -> {
                    String tenantId = context.require("tenantId", String.class);
                    ApiTestClient.ApiResponse response = client.post("/signed/echo")
                            .jsonBody(Map.of(
                                    "message", "scenario iteration " + context.iteration(),
                                    "tenantId", tenantId
                            ))
                            .security(securityHandler("demoHmacSha256"), signContext("local"))
                            .execute();
                    assertEquals(200, response.status());
                    assertTrue(response.verification().success());
                    context.put("echoResponse", response);
                })
                .step("校验场景结果", context -> {
                    ApiTestClient.ApiResponse response = context.require(
                            "echoResponse", ApiTestClient.ApiResponse.class);
                    assertEquals("local-tenant", response.jsonPath("$.echo.tenantId"));
                })
                .afterEach(context -> {
                    // 清理本轮场景产生的数据；即使中间步骤失败也会执行。
                    System.out.println("cleanup iteration " + context.iteration());
                })
                .run();
    }
}
