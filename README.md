# Local API Test Tools

轻量、单机、文件驱动的 Java 接口自动化测试与 Mock 工具。调用侧和 Mock 侧是两个独立进程，共享 YAML/JSON 数据、模型和签名扩展接口，不需要数据库或账号系统。

## 模块

```text
test-tools-core    共享模型、YAML 工作区、HTTP 客户端、断言、签名/验签 SPI
api-test-runner    TestNG 用例入口与标准测试报告
mock-server        Spring Boot MVC 本地 HTTP Mock 与异步回调服务
test-workspace     可提交和共享的用例、套件、Mock 与 fixtures
```

- TestNG 负责编排 YAML 用例、失败状态和 HTML/XML 报告；HTTP 调用使用 JDK `HttpClient`。
- Mock 使用独立 Spring Boot MVC 进程，提供统一异常响应、调用记录和响应后异步回调。
- 两边共享同一个 `HttpSecurityHandler`，同一算法的请求签名、请求验签、响应签名、响应验签不会重复实现。

## 快速开始

要求 Java 21。首次构建会下载 Maven 和项目依赖：

```bash
./mvnw clean package
```

终端一，启动独立 Mock 服务：

```bash
java -jar mock-server/target/mock-server.jar --workspace test-workspace
```

终端二，用 TestNG 执行冒烟套件：

```bash
java -jar api-test-runner/target/api-test-runner.jar \
  --workspace test-workspace --suite smoke
```

也可以只执行一个用例，或省略选择参数执行全部用例：

```bash
java -jar api-test-runner/target/api-test-runner.jar --workspace test-workspace --case signed-echo
java -jar api-test-runner/target/api-test-runner.jar --workspace test-workspace
```

TestNG 报告写入 `test-workspace/results/testng/`，套件中的每个 YAML 用例显示为独立测试。每次执行的完整请求、响应、验签、异常和断言结果另存到 `test-workspace/results/YYYY-MM-DD/`。

## 工作区文件

- `environments/`：目标 URL、默认安全处理器和环境变量。
- `secrets/local-secrets.yaml`：仅本地密钥，默认被 `.gitignore` 排除。
- `fixtures/`：可共享 JSON 请求/响应模型数据，字段可直接增删。
- `cases/`：接口用例、步骤、变量提取与断言。
- `suites/`：用例集合，支持 `repeat` 和 `stopOnFailure`。
- `mocks/`：Mock 匹配与响应定义；运行中修改文件，下一次请求即生效。
- `workspace.yaml`：默认环境、Mock 端口及按接口选择安全算法的规则。

目前只实现 `protocol: http`。字段省略时也按 HTTP 处理；其他协议会返回明确的“不支持”错误，为后续 gRPC/Dubbo 适配保留配置边界。

当前断言支持 `status`、`header`、`jsonPath`、`responseTime`；操作符支持 `equals`、`notNull`、`contains`、`greaterThan`、`lessThan`。

## 为不同接口选择签名算法

处理器解析优先级：

1. 用例步骤的 `securityHandler`
2. `workspace.yaml` 中第一条匹配的 `securityRules`
3. 环境的 `defaultSecurityHandler`
4. `none`

建议优先使用稳定的 `operationId`：

```yaml
securityRules:
  - operationId: createOrder
    handler: orderV2Signature
  - method: POST
    pathPattern: /legacy/**
    handler: legacyMd5Signature
```

单个步骤可覆盖规则：

```yaml
- operationId: specialPayment
  method: POST
  path: /payments
  securityHandler: paymentRsaSignature
```

## 快速新增签名/验签算法

在 `test-tools-core` 实现 `HttpSecurityHandler`。一个处理器有四个独立扩展点，不需要的方向保留接口默认实现：

```java
public final class OrderV2SecurityHandler implements HttpSecurityHandler {
    @Override
    public String id() {
        return "orderV2Signature";
    }

    @Override
    public void signRequest(SignContext context, MutableRequest request) {
        String content = request.method()
                + request.uri().getRawPath()
                + request.bodyText();
        request.header("X-Signature", calculate(context.secret("orderSecret"), content));
    }

    @Override
    public VerificationResult verifyMockRequest(SignContext context, RequestSnapshot request) {
        // 按此接口的协议组装数据并验签
        return VerificationResult.ok();
    }
}
```

然后在 `DefaultSecurityHandlers.create()` 增加一行：

```java
handlers.add(new OrderV2SecurityHandler());
```

重新构建后，调用侧和 Mock 侧会同时获得该算法。也支持把处理器放在独立 jar 中，通过 Java `ServiceLoader<HttpSecurityHandler>` 注册，核心代码无需修改。完整双向示例见 `DemoHmacSecurityHandler`。

## 响应后调用 HTTP 接口

Mock 的 `afterResponse` 会在 Spring MVC 完成原响应处理后异步执行。支持延迟、超时、重试和独立签名算法，失败不会改变已经返回的 Mock 响应：

```yaml
afterResponse:
  - name: order-completed-callback
    protocol: http
    delayMs: 500
    timeoutMs: 3000
    securityHandler: callbackHmac
    retry:
      maxAttempts: 3
      intervalMs: 200
    request:
      method: POST
      url: http://127.0.0.1:8080/order/callback
      headers:
        Content-Type: application/json
      body:
        event: ORDER_COMPLETED
        requestPath: "${request.path}"
        callbackId: "${callback.id}"
```

Mock 管理接口：

- `GET /__testtools/health`：服务状态。
- `GET /__testtools/calls`：最近 100 次 Mock 请求。
- `GET /__testtools/callbacks`：最近 100 个回调的 PENDING、RUNNING、SUCCESS 或 FAILED 状态。

完成的回调记录同时写入 `test-workspace/results/callbacks/YYYY-MM-DD/`。

## 为什么这样拆分

TestNG 是测试编排框架，不是 HTTP 客户端或 Mock 服务器，因此只放在调用侧。Spring Boot 是 Mock 进程宿主，当前仅实现需要的 HTTP 匹配和回调能力；如果未来需要复杂录制回放或故障模拟，可以再把 WireMock 接到 HTTP 引擎内部。gRPC/Dubbo 将作为独立协议适配器加入，不改变现有工作区和结果格式。
