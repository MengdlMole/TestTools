# JUnit API 用例指南

## 推荐目录

```text
api-tests/src/test/java/.../apitest/
├── BasicApiExamples.java
├── JsonFixtureExamples.java
├── SigningExamples.java
├── ApiScenarioExamples.java
└── <业务域>/OrderApiTest.java
```

真实项目可按业务域增加子包。一个测试方法表达一个可以独立运行的接口行为；有关联的多步骤流程放在一个场景方法内。

## 请求 Header、query 和 body

```java
ApiTestClient.ApiResponse response = client.post("/orders")
        .query("source", "local-test")
        .header("tranId", "111")
        .header("timestamp", String.valueOf(Instant.now().getEpochSecond()))
        .header("X-Tenant-Id", "local-tenant")
        .jsonBody(Map.of("productId", "P1001", "quantity", 2))
        .executeVerified();
```

`query` 支持同名多值；当前 Java DSL 的同名 Header 只保留一个值。

## JSON 文件

全局 JSON：

```java
.jsonBodyFile(globalJson("echo-request.json"))
```

用例级 JSON：

```java
.jsonBodyFile(caseJson("create-order", "request.json"))
```

这两种方式读取原始字节，适合“请求体原文参与签名”的接口。需要变量替换时使用：

```java
JsonNode body = resolvedCaseJson(
        "local", "create-order", "request-template.json",
        Map.of("productId", "P1001"));
```

变量覆盖顺序为：`workspace.yaml` → 环境变量 → 本次调用 overrides。

## before 和 after

直接使用 JUnit 生命周期：

```java
@BeforeEach
void setUp() { client = api("local"); }

@AfterEach
void cleanUp() {
    if (createdOrderId != null) {
        client.delete("/orders/" + createdOrderId).execute();
    }
}
```

清理操作尽量放入 `@AfterEach` 或 `ApiScenario.afterEach`，确保断言失败后仍执行。

## 单方法运行

IDE 中点击方法左侧 Run/Debug。命令行：

```bash
./mvnw -pl api-tests -am -Dtest=SigningExamples#signsSelectedHeaderAndJsonBodyFieldsInline -Dsurefire.failIfNoSpecifiedTests=false test
```

示例类以 `Examples` 结尾，因此普通 `./mvnw test` 不会误调用本地服务。

## 关联场景

```java
scenario("下单完整流程")
        .step("创建订单", context -> {
            var response = client.post("/orders").jsonBody(...).executeVerified();
            context.put("orderId", response.jsonPath("$.data.id"));
        })
        .step("查询订单", context -> {
            var response = client.get("/orders/" + context.get("orderId")).executeVerified();
            assertEquals(200, response.status());
        })
        .afterEach(context -> deleteOrder(context.get("orderId")))
        .repeat(5)
        .run();
```

每次 repeat 使用独立 Context，避免上一次运行的数据泄漏到下一次。
