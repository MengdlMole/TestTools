# JUnit API 用例指南

## 核心模型

API Test 刻意只保留三个类型：

- `ApiTestClient`：根据环境创建请求并发送请求。
- `ApiRequest`：测试方法显式组装的 URL、query、Header 和 body。
- `ApiResponse`：供 JUnit 断言使用的响应视图。

框架不接管签名、不隐藏签名执行时机，也不提供自定义断言 DSL。一个测试始终是：

```text
创建 ApiRequest → 组装 query/Header/body → 计算并添加签名 → client.send → JUnit 断言
```

## 推荐目录

```text
api-tests/src/test/java/.../apitest/
├── support/
│   └── ApiTestSupport.java          公共环境、密钥和 JSON 读取入口
├── examples/                        可运行、可复制的框架示例
│   ├── BasicApiExamples.java
│   ├── JsonFixtureExamples.java
│   ├── SigningExamples.java
│   └── ApiScenarioExamples.java
└── cases/                           项目真实用例
    └── <业务域>/OrderApiTest.java
```

`support` 不是用例，不放 `@Test`；`examples` 用于学习框架，并以 `Examples` 结尾避免普通 Maven 构建访问本地服务；实际长期维护的测试全部放在 `cases/<业务域>`。一个测试方法表达一个可独立运行的接口行为，有关联的多步骤流程放在同一个测试方法中。

## 最简单的接口请求

```java
ApiRequest request = client.post("/orders");
request.query("source", "local-test");
request.header("tranId", "111");
request.header("timestamp", String.valueOf(Instant.now().getEpochSecond()));
request.jsonBody(Map.of("productId", "P1001", "quantity", 2));

ApiResponse response = client.send(request);

assertEquals(200, response.status());
assertEquals("CREATED", response.jsonPath("$.data.status"));
```

这里的 `request` 就是即将发送的 `ApiRequest`。调用 `client.send(request)` 前，可以查看它最终的 method、URI、Header 和 body；只有服务端签名协议明确要求的内容才应参与签名，框架不会自动选择字段。

`query` 支持同名多值；同名 Header 后设置的值覆盖先前值。

## 请求日志与脱敏

API 用例默认打印最终请求、响应和耗时，同时隐藏 URI 中的 `user:password`、敏感 query
参数以及 Authorization、Cookie、token、secret、signature 等敏感 Header。全局开关位于
`test-workspace/workspace.yaml`：

```yaml
defaultEnvironment: local
maskSensitiveData: true
```

如果本地调试签名时必须观察原始值，可临时设置为 `false`。也可以对手工创建的客户端单独控制：

```java
ApiTestClient client = ApiTestClient.builder("http://localhost:8080")
        .maskSensitiveData(false)
        .build();
```

关闭后密钥、签名和认证信息会直接进入 IDE/Maven 日志，仅应在可信本机短期使用。

## JSON 文件

API 用例数据统一位于：

```text
test-workspace/fixtures/api-tests/
├── global/                         多个测试类共享
└── cases/<case-name>/              一个用例类或场景专用
```

`case-name` 使用稳定的 kebab-case 名称，通常与测试类对应。例如 `JsonFixtureExamples` 使用 `cases/json-fixture-examples/`，`OrderCreateApiTest` 可以使用 `cases/order-create/`。只有确实被多个测试类复用的数据才放进 `global`，避免它逐渐变成无法判断归属的公共目录。

### 原文发送

需要保留 JSON 文件的空格、换行和字段顺序时，直接发送原始文件字节：

```java
ApiRequest request = client.post("/orders");
request.jsonBodyFile(globalFile("create-order.json"));

ApiResponse response = client.send(request);
```

用例级文件：

```java
request.jsonBodyFile(caseFile("create-order", "request.json"));
```

这种方式适合“完整 body 原文参与签名”的接口。不要读取后重新序列化，否则空格或字段顺序可能变化。

### 读取后修改

JSON 根节点是对象时，可以读取为 `ObjectNode` 后继续修改：

```java
ObjectNode body = globalJsonObject("create-order.json");
body.put("productId", "P1001");
body.put("quantity", 2);
body.withObject("receiver").put("city", "Shanghai");

ApiRequest request = client.post("/orders");
request.jsonBody(body);
```

用例级数据使用：

```java
ObjectNode body = caseJsonObject("create-order", "request.json");
```

需要替换 `${tenantId}` 等变量时：

```java
JsonNode body = resolvedGlobalJson(
        "local", "create-order-template.json", Map.of("productId", "P1001"));
request.jsonBody(body);
```

变量覆盖顺序为：`workspace.yaml` → 环境变量 → 本次调用 overrides。

## 显式签名

签名是被测接口协议的一部分，放在对应测试类中，测试代码按真实协议显式表达算法和字段顺序：

```java
ObjectNode body = globalJsonObject("header-body-request.json");
body.put("name", 333);

String tranId = "111";
String timestamp = "222";
ApiRequest request = client.post("/signed/header-body");
request.header("tranId", tranId);
request.header("timestamp", timestamp);
request.jsonBody(body);

// /signed/header-body 的服务端验签协议规定以下字段名称、顺序和拼接方式。
String contentToSign = "tranId" + tranId
        + "timestamp" + timestamp
        + "name" + body.path("name").asText();
String signature = hmacSha256(secret("local", "appSecret"), contentToSign);

request.header("X-App-Key", secret("local", "appKey"));
request.header("X-Signature", signature);

ApiResponse response = client.send(request);
assertEquals(200, response.status());
```

框架不猜测哪些字段参与签名，也不自动排序或拼接。开发者直接使用 Java 编写协议；常用 HMAC-SHA256 可调用 `HmacSha256.signHex(...)`，也可以像 `SigningExamples` 一样保留当前接口专属的 `Mac` 实现，方便单步调试。

## before 和 after

直接使用 JUnit 生命周期：

```java
@BeforeEach
void setUp() {
    client = api("local");
}

@AfterEach
void cleanUp() {
    if (createdOrderId != null) {
        ApiRequest request = client.delete("/orders/" + createdOrderId);
        client.send(request);
    }
}
```

## 单方法运行

IDE 中点击方法左侧 Run/Debug。命令行：

```bash
./mvnw -pl api-tests -am -Dtest=SigningExamples#signsSelectedHeaderAndJsonFields -Dsurefire.failIfNoSpecifiedTests=false test
```

示例类以 `Examples` 结尾，因此普通 `./mvnw test` 不会误调用本地服务。

## 关联场景

关联接口不需要额外 DSL。把同一场景的调用按顺序写在一个 JUnit 方法中，使用普通局部变量传递数据：

```java
@Test
void createQueryAndDeleteOrder() {
    String orderId = null;
    try {
        orderId = createOrder();
        ApiResponse response = queryOrder(orderId);
        assertEquals("CREATED", response.jsonPath("$.data.status"));
    } finally {
        if (orderId != null) {
            deleteOrder(orderId);
        }
    }
}
```

需要重复执行时使用普通循环或 JUnit `@RepeatedTest`。可运行示例见 `ApiScenarioExamples`。
