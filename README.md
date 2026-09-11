# Local API Test Tools

面向本地开发的轻量 Java 接口测试工具，核心解决两件事：

1. 使用 JUnit 5 像单元测试一样调用服务 API，可对单个方法 Run、Debug 并查看完整日志。
2. 使用独立 Spring Boot Mock Server 模拟项目依赖的 HTTP 接口，并可在响应后异步回调其他服务。

推荐使用 JUnit 代码化用例；TestNG/YAML runner 只用于可选的文件化批量回归。当前实现 HTTP，RPC/Dubbo 作为后续独立协议适配器扩展。

## 模块与边界

```text
api-tests                     项目自己的 JUnit API 用例和关联场景
        │
        ▼
test-tools-core               HTTP DSL、环境/密钥/fixture、签名 SPI
        ▲                                  ▲
        │                                  │
mock-server                   project-security
Spring Boot HTTP 测试桩        项目/API 专属签名与验签实现

yaml-test-runner              可选 TestNG/YAML 批量执行器
test-workspace                可通过文件共享的配置、JSON、用例和 Mock
```

`test-tools-core` 使用 `core` 而不是 `common`：它提供稳定的核心 API 和扩展契约，不作为无归属代码的公共目录。YAML 和 Mock 的配置模型分别归自己的模块所有。

各模块允许和禁止承载的职责见 [架构与命名约定](docs/architecture.md)。

## 5 分钟运行示例

要求 Java 21。首次执行会下载 Maven 和项目依赖。

```bash
cp test-workspace/secrets/local-secrets.example.yaml test-workspace/secrets/local-secrets.yaml

./mvnw clean package
java -jar mock-server/target/mock-server.jar --workspace test-workspace
```

然后在 IDEA 或 VS Code 中打开：

```text
api-tests/src/test/java/io/github/mengdlmole/testtools/apitest/BasicApiExamples.java
```

点击 `health()` 左侧图标即可单独 Run 或 Debug。IDE 控制台会显示最终 URL、Header、请求体、响应和验签结果。

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

在 `api-tests/src/test/java/` 下创建普通 JUnit 5 测试类：

```java
package io.github.mengdlmole.testtools.apitest;

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
        ApiTestClient.ApiResponse response = client.get("/orders/1001")
                .header("tranId", "111")
                .header("timestamp", String.valueOf(Instant.now().getEpochSecond()))
                .executeVerified();

        assertEquals(200, response.status());
        assertEquals("1001", response.jsonPath("$.data.id"));
    }
}
```

这是普通 JUnit 测试，支持断点、生命周期、参数化测试、`@Nested`、IDE 日志和测试报告。完整说明见 [JUnit API 用例指南](docs/junit-api-tests.md)。

### 3. 使用持久化 JSON 请求体

```text
test-workspace/fixtures/global/create-order.json          所有用例可复用
test-workspace/fixtures/cases/create-order/request.json  仅 create-order 使用
```

原样读取并发送，保留空格、换行和字段顺序：

```java
client.post("/orders")
        .jsonBodyFile(globalJson("create-order.json"))
        .executeVerified();

client.post("/orders")
        .jsonBodyFile(caseJson("create-order", "request.json"))
        .executeVerified();
```

需要替换 `${tenantId}` 或在 Java 中修改 JSON 时：

```java
JsonNode body = resolvedGlobalJson(
        "local", "create-order-template.json", Map.of("productId", "P1001"));
client.post("/orders").jsonBody(body).executeVerified();
```

### 4. 在当前接口用例中签名

接口独有的签名规则直接放在相应测试类附近。例如按固定顺序签名：

```text
tranId111timestamp222name333
```

```java
client.post("/signed/header-body")
        .header("tranId", "111")
        .header("timestamp", "222")
        .jsonBodyFile(globalJson("header-body-request.json"))
        .signWith(request -> {
            String signData = headerBodySignData(request);
            request.header("X-App-Key", secret("local", "appKey"));
            request.header("X-Signature",
                    hmacSha256(secret("local", "appSecret"), signData));
        })
        .executeVerified();
```

签名回调在 URL、query、Header 和 body 全部组装后执行，应从 `request` 读取最终值。`SigningExamples` 保留直接使用 Java `Mac` 的自包含示例；正式复用代码可调用 core 的 `HmacSha256`。算法选择、字段拼接和 ServiceLoader 注册见 [签名与验签扩展](docs/security.md)。

### 5. 运行关联场景

多个互相独立的测试方法直接运行整个测试类。需要顺序执行并共享 ID 时使用 `ApiScenario`：

```java
@Test
void createAndQueryOrder() {
    scenario("创建并查询订单")
            .step("创建", context -> context.put("orderId", createOrder()))
            .step("查询", context -> queryOrder(context.get("orderId")))
            .afterEach(context -> deleteOrder(context.get("orderId")))
            .repeat(3)
            .run();
}
```

可运行示例见 `ApiScenarioExamples`。

## 新增 Spring Boot Mock

在 `test-workspace/mocks/` 新建 YAML：

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

Mock Server 每次请求都会重新读取配置，修改 YAML 后通常不需要重启。可配置 query、Header、JSON body 匹配、响应文件、验签、延迟和 `afterResponse` 回调。完整字段及示例见 [Mock Server 指南](docs/mock-server.md)。

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
│   ├── global/                    全局 JSON
│   └── cases/<case>/              用例级 JSON
├── mocks/                         Mock 定义
├── mock-server.yaml               Mock Server 配置
├── cases/                         可选 YAML 调用用例
├── suites/                        可选 YAML 用例集合
├── yaml-runner.yaml               可选 YAML runner 配置
└── results/                       运行结果；不提交
```

未显式指定工作区时，JUnit、YAML runner 和 Mock Server 都会从当前目录向上查找 `test-workspace/workspace.yaml`。也可以通过 `-Dtesttools.workspace=/绝对路径` 指定；可执行 jar 的 `--workspace` 参数优先级最高。

## 文档索引

- [架构、模块边界和命名约定](docs/architecture.md)
- [JUnit API 用例指南](docs/junit-api-tests.md)
- [签名与验签扩展](docs/security.md)
- [Spring Boot Mock Server](docs/mock-server.md)
- [可选 TestNG/YAML runner](docs/yaml-runner.md)
- [常见问题与当前边界](docs/troubleshooting.md)

## 验证

```bash
./mvnw clean test
./mvnw package

java -jar mock-server/target/mock-server.jar --workspace test-workspace
java -jar yaml-test-runner/target/yaml-test-runner.jar --workspace test-workspace --suite smoke
```
