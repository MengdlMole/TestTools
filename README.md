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

## 新增一个调用接口的 TestNG 用例

通常只需要新增 YAML/JSON 文件，不需要编写 TestNG Java 类。`FileDrivenApiTest` 会自动扫描 `cases/`，并把每个 YAML 用例作为一条独立的 TestNG 测试执行。

### 1. 确认调用环境

复用或新增 `test-workspace/environments/<环境名>.yaml`：

```yaml
name: local
baseUrl: http://127.0.0.1:8080
secretRef: order-service
defaultSecurityHandler: none

variables:
  tenantId: local-tenant
```

- `baseUrl` 是被测服务地址，步骤中的 `path` 会拼接在它后面。
- `secretRef` 指向本机密钥组；没有鉴权时可以省略。
- `defaultSecurityHandler` 是该环境的默认签名算法，也可以在单个步骤或 `workspace.yaml` 规则中覆盖。
- 新增非默认环境时，用例的 `environment` 填写该文件名；若省略，则使用 `workspace.yaml` 的 `defaultEnvironment`。

本机密钥放在 `test-workspace/secrets/local-secrets.yaml`：

```yaml
secrets:
  order-service:
    appKey: local-app-key
    appSecret: local-app-secret
```

该文件已被 `.gitignore` 排除，不应提交。需要共享字段结构时，更新可提交的 `test-workspace/secrets/local-secrets.example.yaml`，值使用占位符。

### 2. 准备请求模型或预制数据

较大的 JSON 请求建议放在 `test-workspace/fixtures/`，例如 `test-workspace/fixtures/create-order.json`：

```json
{
  "tenantId": "${tenantId}",
  "productId": "P1001",
  "quantity": 2
}
```

fixture 以及步骤的 `path`、query 值、header 值和请求 `body` 中都可以使用 `${变量名}`。变量覆盖顺序为：`workspace.yaml variables` → 环境 variables → 用例 variables → 前序步骤 extract 的结果。断言的 `expected` 当前使用直接值，不进行变量替换。

### 3. 新增用例文件

在 `test-workspace/cases/` 新增 `<用例标识>.yaml`，例如 `create-order.yaml`：

```yaml
name: 创建订单成功
environment: local

steps:
  - name: 创建订单
    protocol: http
    operationId: createOrder
    method: POST
    path: /orders
    query:
      source: local-test
    headers:
      X-Tenant-Id: "${tenantId}"
    bodyFile: fixtures/create-order.json
    # 简短请求也可以直接使用 body，body 与 bodyFile 二选一。
    extract:
      orderId: $.data.id
    assertions:
      - type: status
        expected: 201
      - type: header
        path: Content-Type
        operator: contains
        expected: application/json
      - type: jsonPath
        path: $.data.status
        expected: CREATED
      - type: responseTime
        operator: lessThan
        expected: 2000

  - name: 查询刚创建的订单
    protocol: http
    operationId: getOrder
    method: GET
    path: /orders/${orderId}
    assertions:
      - type: status
        expected: 200
```

注意：

- 文件名 `<用例标识>` 是命令行 `--case` 和套件 `cases` 使用的名称，不是 YAML 中展示用的 `name`。
- `operationId` 建议保持稳定，用于给不同接口选择签名算法。
- `extract` 的值是 JSONPath，提取结果可被后续步骤通过 `${变量名}` 使用。
- `expected` 支持字符串、数字、布尔值等 JSON 类型；类型应与实际响应一致。
- 请求体非空时工具会自动补充 `Content-Type: application/json`，显式 header 优先。

### 4. 按需加入测试套件

若要随冒烟测试执行，在 `test-workspace/suites/smoke.yaml` 的 `cases` 中加入文件名：

```yaml
name: 本地冒烟测试
stopOnFailure: true
repeat: 1

cases:
  - plain-health
  - create-order
```

`stopOnFailure: true` 会在首次失败后跳过套件中的剩余用例；`repeat` 用于重复执行整个用例列表，最大值为 100。

### 5. 执行并查看结果

```bash
# 单个用例
java -jar api-test-runner/target/api-test-runner.jar \
  --workspace test-workspace --case create-order

# 整个套件
java -jar api-test-runner/target/api-test-runner.jar \
  --workspace test-workspace --suite smoke
```

重点查看：

- `test-workspace/results/testng/`：TestNG HTML/XML 报告。
- `test-workspace/results/YYYY-MM-DD/`：请求、响应、断言、验签和异常明细。

## 新增一个 HTTP 测试桩（Mock）

Mock Server 会在每次请求时重新读取 `test-workspace/mocks/`，因此新增或修改 Mock YAML 后通常不需要重启进程。

### 1. 新增 Mock 定义

在 `test-workspace/mocks/` 新增 `<Mock标识>.yaml`，例如 `create-order.yaml`：

```yaml
name: 创建订单 Mock
protocol: http
enabled: true
priority: 10
securityHandler: none

request:
  method: POST
  path: /orders
  query:
    source: local-test
  headers:
    X-Tenant-Id: local-tenant
  body:
    tenantId: local-tenant
    productId: P1001
    quantity: 2

response:
  status: 201
  headers:
    Content-Type: application/json
  delayMs: 50
  body:
    data:
      id: mock-order-001
      status: CREATED
```

匹配规则：

- `method`、`path` 以及已配置的 query/header 必须相等；未配置的项目不参与匹配。
- 配置 `request.body` 时，收到的请求体必须是结构相同的 JSON。
- 多个 Mock 都能匹配时，`priority` 数值较小的先匹配；省略时默认为 100。
- `enabled: false` 可临时关闭 Mock。
- 响应较大时，可用 `response.bodyFile: fixtures/xxx.json` 替代内联 `body`。
- `securityHandler` 用于校验收到的请求并对 Mock 响应签名；无需鉴权时写 `none` 或省略。

### 2. 如需响应后调用服务接口，配置 afterResponse

在同一个 Mock 文件中增加：

```yaml
afterResponse:
  - name: order-created-callback
    protocol: http
    delayMs: 500
    timeoutMs: 3000
    securityHandler: none
    retry:
      maxAttempts: 3
      intervalMs: 200
    request:
      method: POST
      url: http://127.0.0.1:8080/order/callback
      headers:
        X-Source: local-mock
      body:
        event: ORDER_CREATED
        sourceMock: "${mock.name}"
        originalPath: "${request.path}"
        callbackId: "${callback.id}"
```

回调会在原 Mock 响应处理完成后异步发出，回调失败不会修改已经返回给调用方的响应。回调可使用 `${mock.name}`、`${request.method}`、`${request.path}` 和 `${callback.id}`；也可使用工作区与默认环境的变量。`securityHandler` 可与原 Mock 使用不同的已注册签名算法。

### 3. 启动并验证 Mock

```bash
java -jar mock-server/target/mock-server.jar --workspace test-workspace

curl -i -X POST 'http://127.0.0.1:19090/orders?source=local-test' \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: local-tenant' \
  -d '{"tenantId":"local-tenant","productId":"P1001","quantity":2}'
```

可通过管理接口排查匹配和回调：

- `GET /__testtools/health`：确认 Mock Server 存活。
- `GET /__testtools/calls`：查看最近 100 次请求、响应状态和命中的 Mock。
- `GET /__testtools/callbacks`：查看最近 100 个回调的执行状态和失败原因。
- `test-workspace/results/callbacks/YYYY-MM-DD/`：查看已完成回调的持久化记录。

### 4. Mock 使用鉴权时

调用侧与 Mock 侧共用 `HttpSecurityHandler`：调用用例负责 `signRequest` 和 `verifyResponse`，Mock 负责 `verifyMockRequest` 和 `signMockResponse`。在 Mock、用例步骤或 `workspace.yaml securityRules` 中填写的处理器 ID 必须已注册；新增算法的具体步骤见下一节。

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

在 `test-tools-core/src/main/java/io/github/localtools/testtools/security/` 新增处理器类，例如 `OrderV2SecurityHandler.java`，并实现 `HttpSecurityHandler`。一个处理器有四个独立扩展点，不需要的方向保留接口默认实现：

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

然后在同目录的 `DefaultSecurityHandlers.java` 的 `create()` 方法中增加一行：

```java
handlers.add(new OrderV2SecurityHandler());
```

重新构建后，调用侧和 Mock 侧会同时获得该算法。也支持把处理器放在独立 jar 中，通过 Java `ServiceLoader<HttpSecurityHandler>` 注册，核心代码无需修改。完整双向示例见 `DemoHmacSecurityHandler`。

## 为什么这样拆分

TestNG 是测试编排框架，不是 HTTP 客户端或 Mock 服务器，因此只放在调用侧。Spring Boot 是 Mock 进程宿主，当前仅实现需要的 HTTP 匹配和回调能力；如果未来需要复杂录制回放或故障模拟，可以再把 WireMock 接到 HTTP 引擎内部。gRPC/Dubbo 将作为独立协议适配器加入，不改变现有工作区和结果格式。
