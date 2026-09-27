# Local API Test Tools

面向本地开发的轻量 Java 接口测试工具，核心解决两件事：

1. 使用 JUnit 5 像单元测试一样调用服务 API，可对单个方法 Run、Debug 并查看完整日志。
2. 使用独立 Spring Boot Mock Server 模拟项目依赖的 HTTP 接口，并可在响应后异步回调其他服务。

当前只实现 HTTP。RPC/Dubbo 如有明确需求，应作为独立协议适配器扩展。

## 模块与边界

```text
api-tests                     项目自己的普通 JUnit API 用例
        │
        ▼
test-tools-core               简单 HTTP 客户端、环境/密钥/fixture、密码学与 Mock 安全 SPI
        ▲                                  ▲
        │                                  │
mock-server                   project-security
Spring Boot HTTP 测试桩        Mock 请求、响应和回调的安全协议实现

test-workspace                可通过文件共享的环境、JSON 和 Mock 配置
```

`test-tools-core` 使用 `core` 而不是 `common`：它提供稳定的核心 API 和扩展契约，不作为无归属代码的公共目录。Mock 配置模型仍归 Mock 模块所有。

各模块允许和禁止承载的职责见 [架构与命名约定](docs/architecture.md)。

项目以 Google Java Style 为基线，并统一使用多行 Javadoc。提交前运行 `./mvnw spotless:apply` 自动格式化，再运行 `./mvnw verify` 完成测试、格式和编码规范检查；详细约定见 [编码规范与格式化](docs/code-style.md)。

## 5 分钟运行示例

要求 Java 21。首次执行会下载 Maven 和项目依赖。

```bash
cp test-workspace/secrets/local-secrets.example.yaml test-workspace/secrets/local-secrets.yaml

./mvnw clean package
java -jar mock-server/target/mock-server.jar --workspace test-workspace
```

然后在 IDEA 或 VS Code 中打开：

```text
api-tests/src/test/java/io/github/mengdlmole/testtools/apitest/examples/BasicApiExamples.java
```

点击 `health()` 左侧图标即可单独 Run 或 Debug。IDE 控制台会显示最终 URL、Header、请求体和响应。

命令行运行同一个方法：

```bash
./mvnw -pl api-tests -am -Dtest=BasicApiExamples#health -Dsurefire.failIfNoSpecifiedTests=false test
```

## 新增 JUnit API 用例

### 1. 配置目标环境

编辑或新增 `test-workspace/environments/<环境名>.yaml`：

```yaml
name: local
baseUrl: http://127.0.0.1:19090
secretRef: demo

variables:
  tenantId: local-tenant
```

密钥只放在被 Git 忽略的 `test-workspace/secrets/local-secrets.yaml`：

```yaml
secrets:
  demo:
    appKey: local-app-key
    appSecret: local-app-secret
```

### 2. 新建可独立调试的测试

在 `api-tests/src/test/java/.../apitest/cases/<业务域>/` 下创建普通 JUnit 5 测试类：

```java
package io.github.mengdlmole.testtools.apitest.cases.order;

import io.github.mengdlmole.testtools.apitest.support.ApiTestSupport;
import io.github.mengdlmole.testtools.http.ApiRequest;
import io.github.mengdlmole.testtools.http.ApiResponse;
import io.github.mengdlmole.testtools.http.ApiTestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderApiTest extends ApiTestSupport {
    private ApiTestClient client;

    @BeforeEach
    void setUp() {
        client = api("local");
    }

    @AfterEach
    void tearDown() {
        // 按业务需要清理本用例创建的数据。
    }

    @Test
    void queryOrder() {
        ApiRequest request = client.get("/orders/1001");
        request.header("tranId", "111");
        request.header("timestamp", String.valueOf(Instant.now().getEpochSecond()));

        ApiResponse response = client.send(request);

        assertEquals(200, response.status());
        assertEquals("1001", response.jsonPath("$.data.id"));
    }
}
```

这是普通 JUnit 测试，支持断点、生命周期、参数化测试、`@Nested`、IDE 日志和测试报告。完整说明见 [JUnit API 用例指南](docs/junit-api-tests.md)。

### 3. 使用持久化 JSON 请求体

```text
test-workspace/fixtures/api-tests/global/create-order.json          所有 API 用例可复用
test-workspace/fixtures/api-tests/cases/create-order/request.json   仅 create-order 用例使用
```

原样读取并发送，保留空格、换行和字段顺序：

```java
ApiRequest request = client.post("/orders");
request.jsonBodyFile(globalFile("create-order.json"));
ApiResponse response = client.send(request);

ApiRequest caseRequest = client.post("/orders");
caseRequest.jsonBodyFile(caseFile("create-order", "request.json"));
ApiResponse caseResponse = client.send(caseRequest);
```

读取后继续修改 JSON：

```java
ObjectNode body = globalJsonObject("create-order.json");
body.put("productId", "P1001");
body.put("quantity", 2);

ApiRequest request = client.post("/orders");
request.jsonBody(body);
ApiResponse response = client.send(request);
```

### 4. 在当前接口用例中签名

接口独有的签名规则直接放在相应测试类附近。例如按固定顺序签名：

```text
tranId111timestamp222name333
```

```java
ObjectNode body = globalJsonObject("header-body-request.json");
body.put("name", 333);

String tranId = "111";
String timestamp = "222";
ApiRequest request = client.post("/signed/header-body");
request.header("tranId", tranId);
request.header("timestamp", timestamp);
request.jsonBody(body);

// 这是 /signed/header-body 的服务端协议，不是框架规定的通用规则。
String contentToSign = "tranId" + tranId
        + "timestamp" + timestamp
        + "name" + body.path("name").asText();
request.header("X-App-Key", secret("local", "appKey"));
request.header("X-Signature",
        hmacSha256(secret("local", "appSecret"), contentToSign));

ApiResponse response = client.send(request);
```

没有签名回调或隐藏执行顺序：`request` 是当前用例正在组装的 `ApiRequest`。哪些数据参与签名完全由服务端接口协议决定，HTTP method、path 等不会被框架自动加入。`SigningExamples` 保留直接使用 Java `Mac` 的自包含示例；也可调用 core 的 `HmacSha256`。详细说明见 [签名与验签](docs/security.md)。

### 5. 运行关联场景

多个互相独立的测试方法可以直接运行整个测试类。需要顺序执行并共享 ID 时，在一个 JUnit 方法中使用普通 Java 代码：

```java
@Test
void createAndQueryOrder() {
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

需要反复运行时使用 `@RepeatedTest` 或普通循环。可运行示例见 `ApiScenarioExamples`。

## 新增 Spring Boot Mock

在 `test-workspace/mocks/cases/<业务域>/` 新建 YAML，例如
`test-workspace/mocks/cases/order/query-order.yaml`：

```yaml
name: 查询订单
enabled: true
priority: 10

request:
  method: GET
  path: /orders/1001

response:
  status: 200
  body:
    data:
      id: "1001"
      status: CREATED
```

Mock Server 会递归读取 `mocks/`，每次请求都会重新加载配置，修改 YAML 后通常不需要重启。`mocks/examples/` 是工具自带示例，`mocks/cases/<业务域>/` 才放项目实际使用的接口测试桩。可配置 query、Header、JSON body 匹配、响应文件、验签、延迟和 `afterResponse` 回调。完整字段及示例见 [Mock Server 指南](docs/mock-server.md)。

Mock 端口属于 Mock 模块，配置在 `test-workspace/mock-server.yaml`：

```yaml
port: 19090
```

## 工作区目录

```text
test-workspace/
├── workspace.yaml                 跨模块公共配置
├── environments/                  URL、环境变量和密钥引用
├── secrets/                       本地密钥；真实值不提交
├── fixtures/
│   └── api-tests/
│       ├── global/                所有 JUnit API 用例可复用的 JSON
│       └── cases/<case>/          由一个用例或场景拥有的 JSON
├── mocks/
│   ├── examples/                  工具自带、与 API 示例配套的 Mock
│   └── cases/<业务域>/            项目真实 Mock 接口定义
├── mock-server.yaml               Mock Server 配置
└── results/                       运行结果；不提交
```

未显式指定工作区时，JUnit 和 Mock Server 都会从当前目录向上查找 `test-workspace/workspace.yaml`。也可以通过 `-Dtesttools.workspace=/绝对路径` 指定；Mock Server 的 `--workspace` 参数优先级最高。

## 文档索引

- [架构、模块边界和命名约定](docs/architecture.md)
- [编码规范与格式化](docs/code-style.md)
- [JUnit API 用例指南](docs/junit-api-tests.md)
- [签名与验签扩展](docs/security.md)
- [Spring Boot Mock Server](docs/mock-server.md)
- [常见问题与当前边界](docs/troubleshooting.md)

## 验证

```bash
./mvnw clean test
./mvnw package

java -jar mock-server/target/mock-server.jar --workspace test-workspace
```
