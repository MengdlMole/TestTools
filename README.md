# Local API Test Tools

轻量、单机、代码优先并兼容文件驱动的 Java 接口自动化测试与 Mock 工具。调用侧和 Mock 侧相互独立，共享环境、密钥、模型和签名扩展接口，不需要数据库或账号系统。

## 模块

```text
test-tools-core    Code-first HTTP DSL、共享模型、YAML 工作区、签名/验签 SPI
api-test-runner    JUnit 单用例与可选的 TestNG/YAML 批量测试入口
mock-server        Spring Boot MVC 本地 HTTP Mock 与异步回调服务
test-workspace     可提交和共享的用例、套件、Mock 与 fixtures
```

- JUnit 5 + `ApiTestClient` 是推荐的核心模式：一个测试方法就是一个接口用例，可单独 Run/Debug。
- TestNG/YAML 用于需要数据文件复用、批量套件和 HTML/XML 报告的集成测试。
- Mock 使用独立 Spring Boot MVC 进程，提供统一异常响应、调用记录和响应后异步回调。
- 两边共享同一个 `HttpSecurityHandler`，同一算法的请求签名、请求验签、响应签名、响应验签不会重复实现。

## 如何选择运行方式

| 需求 | 推荐入口 | 数据位置 | 运行方式 |
| --- | --- | --- | --- |
| 开发中调试单个接口 | JUnit + `ApiTestClient` | Java 测试方法、fixture | IDE 点击单个方法 Run/Debug |
| 一组有关联的接口反复验证 | JUnit + `ApiScenario` | Java 场景及共享 Context | IDE 点击场景方法，或 Maven 指定方法 |
| 可共享的批量回归 | TestNG + YAML | `cases/`、`suites/`、`fixtures/` | jar、Maven 或 IDE Launcher |
| 给项目提供 HTTP 测试桩 | Spring Boot Mock Server | `mocks/`、`fixtures/` | 独立 jar 或 IDE 启动 |

优先从 JUnit 代码化用例开始；只有当数据需要脱离代码复用或组成批量回归时，再增加 YAML/TestNG 层。两种模式可以同时使用。

## 可运行示例索引

| 示例 | 代码/配置 | 演示内容 |
| --- | --- | --- |
| 单接口 JUnit | `CodeFirstApiExamples.java` | before/after、普通请求、用例内签名、复用签名处理器 |
| query + body 签名 | `QueryBodyHmacSecurityHandler.java`、`mocks/query-body-signed.yaml` | 同名 query、编码后排序、原始 body、双向签名/验签 |
| 关联场景 | `AssociatedApiScenarioExamples.java` | 多步骤传值、整条链路重复、失败后清理 |
| YAML 单用例 | `cases/signed-echo.yaml`、`IdeApiTestLauncher.java` | 数据持久化、单用例 IDE Run/Debug |
| TestNG 套件 | `suites/smoke.yaml` | 批量执行、重复与 HTML/XML 报告 |
| Mock + 回调 | `mocks/signed-echo.yaml`、`mocks/callback-receiver.yaml` | 请求验签、响应签名、响应后异步调用 |

## 快速开始

要求 Java 21。首次构建会下载 Maven 和项目依赖：

```bash
./mvnw clean package
```

终端一，启动独立 Mock 服务：

```bash
java -jar mock-server/target/mock-server.jar --workspace test-workspace
```

然后可在 IDEA/VS Code 中打开 `CodeFirstApiExamples.java`，点击任意 `@Test` 方法单独 Run/Debug。也可以从终端只运行 query + body 签名示例：

```bash
./mvnw -pl api-test-runner -am \
  -Dtest=CodeFirstApiExamples#signedQueryAndBodyWithReusableHandler \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

如本机还没有密钥文件，先复制示例并替换其中的值：

```bash
cp test-workspace/secrets/local-secrets.example.yaml \
  test-workspace/secrets/local-secrets.yaml
```

`local-secrets.yaml` 已被 Git 忽略。仓库示例的调用侧和 Mock 侧读取同一份本机密钥，因此保持两边一致即可。

如需文件驱动的批量回归，用 TestNG 执行冒烟套件：

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

## 推荐核心模式：JUnit 代码化接口用例

代码化用例适合日常接口开发和调试：一条 `@Test` 方法对应一个接口场景，请求、签名和断言全部在方法内可见并可打断点。框架不规定签名原文或密钥格式，只提供通用 HTTP DSL、环境/密钥读取、可复用签名器和脱敏日志。

示例入口位于 `api-test-runner/src/test/java/io/github/localtools/testtools/runnercli/CodeFirstApiExamples.java`。在 IDEA 或 VS Code 中点击任意方法左侧图标即可单独 Run/Debug。

### 新建测试类及 before/after

测试类继承 `ApiTestSupport`，正常使用 JUnit 5 生命周期：

```java
public class OrderApiTest extends ApiTestSupport {
    private ApiTestClient client;
    private String orderId;

    @BeforeEach
    void setUp() {
        client = api("local"); // 读取 environments/local.yaml 的 baseUrl
    }

    @AfterEach
    void tearDown() {
        // 按业务需要清理本用例创建的数据
        if (orderId != null) {
            client.delete("/orders/" + orderId).execute();
        }
    }

    @Test
    void createOrder() {
        // 单个方法可以直接 Run 或 Debug
    }
}
```

如果这类测试依赖必须手动启动的本地服务，可把类命名为 `*Examples` 或 `*Launcher`，避免普通 `mvn test` 自动执行；需要纳入持续集成时，使用标准的 `*Test` 类名。

### 在用例内完全自定义请求和签名

下面的签名规则由这个用例自行决定：选择 `local` 环境的 `appSecret`，把编码后的 `queryString + bodyString` 直接拼接，再计算 HMAC-SHA256 并写入 header。

```java
@Test
void createOrderWithCustomSignature() {
    String appKey = secret("local", "appKey");
    String appSecret = secret("local", "appSecret");

    ApiTestClient.ApiResponse response = client.post("/orders")
            .query("tenantId", "T1001")
            .query("timestamp", System.currentTimeMillis())
            .header("X-App-Key", appKey)
            .jsonBody(Map.of(
                    "productId", "P1001",
                    "quantity", 2
            ))
            .signWith(request -> {
                // 此时 URI、query、header 和 body 已全部构造完成。
                String queryString = request.uri().getRawQuery();
                String bodyString = request.bodyText();
                String signData = queryString + bodyString;
                String signature = hmacSha256(appSecret, signData);
                request.header("X-Signature", signature);
            })
            .verifyWith((request, httpResponse) -> {
                // 不需要响应验签时可以省略 verifyWith。
                String actual = httpResponse.firstHeader("X-Response-Signature");
                String expected = hmacSha256(appSecret, httpResponse.bodyText());
                return expected.equals(actual)
                        ? VerificationResult.ok()
                        : VerificationResult.failed("response signature mismatch");
            })
            .executeVerified();

    assertEquals(201, response.status());
    orderId = String.valueOf(response.jsonPath("$.data.id"));
}
```

示例中的 `hmacSha256` 是普通测试辅助方法，可直接放在测试类中；完整实现可参考 `CodeFirstApiExamples` 或后文的自定义签名处理器示例。

这个 lambda 可以替换成任意规则，例如：

- method + path + timestamp + body。
- query 按 key 排序后拼接，排除空值或签名字段。
- 从 JSON 中只提取部分业务字段。
- MD5、HMAC、RSA 或项目自定义算法。
- 把签名写入 header、query，或在签名前修改 body。

`signWith` 在最终 URI 和 body 构建完毕后执行，因此签名看到的就是即将发送的数据。测试用例可以直接从代码、环境变量或 `secret("环境", "字段")` 选择密钥。`local-secrets.yaml` 仍不会提交到 Git。

### 使用可复用签名算法

多个用例使用相同协议时，把算法实现为 `HttpSecurityHandler`，用例中直接选择算法和该环境的签名上下文：

```java
ApiTestClient.ApiResponse response = client.post("/signed/echo")
        .jsonBody(requestModel)
        .security(
                securityHandler("demoHmacSha256"),
                signContext("local")
        )
        .executeVerified();
```

`security(...)` 会同时调用处理器的 `signRequest` 和 `verifyResponse`。`executeVerified()` 在响应验签失败时直接抛出 `AssertionError`，推荐用于配置了验签的 JUnit 用例；`execute()` 会返回失败的 `verification` 供负向测试自行断言。某个用例有特殊差异时，可以继续使用 `signWith`/`verifyWith` 覆盖为用例内逻辑，不必为了一个例外修改通用处理器。

### 请求与响应能力

`ApiTestClient` 支持：

- `get/post/put/patch/delete/request`，path 或完整 URL。
- `query/queries`、`header/headers`；多次调用 `query` 可发送同名参数并保持添加顺序。
- `body(String)`、`body(byte[])`、`bodyFile(Path)`、`jsonBody(Object)`。
- 单请求 `timeout(Duration)`。
- `status()`、`header()`、`body()`、`json()`、`jsonPath()` 和 `durationMs()`。
- 默认输出脱敏后的请求/响应日志；签名、Authorization、Cookie、API Key 等 header 显示为 `***`。

`jsonBody` 会序列化对象并自动补充 `Content-Type: application/json`；`body` 和 `bodyFile` 不猜测媒体类型，需要用例显式设置 header。包含 token、secret、signature、api-key 等名称的 query 值也会在日志和记录中脱敏。

JUnit 断言、参数化测试、嵌套测试、before/after、测试夹具和调试器都可以正常使用。框架不会把这些能力重新封装一遍。

### 关联用例一起运行和重复场景

如果多个用例只是属于同一需求但彼此独立，把它们放进同一个 JUnit 测试类即可：点击方法只运行一个，点击类名则一起运行全部方法。也可以使用 JUnit `@Nested` 或 `@Tag` 继续分组。

如果多个接口存在先后依赖、需要共享上一步产生的 ID，并且要反复执行整条链路，使用 `ApiScenario`。一个 JUnit 方法代表完整场景，内部拆成多个有名称的步骤：

```java
@Test
void orderPaymentScenario() {
    scenario("下单并支付")
            .repeat(Integer.getInteger("testtools.repeat", 1))
            .beforeEach(context -> {
                client = api("local");
                context.put("tenantId", "T1001");
            })
            .step("创建订单", context -> {
                ApiTestClient.ApiResponse response = client.post("/orders")
                        .jsonBody(Map.of("tenantId", context.get("tenantId")))
                        .execute();
                assertEquals(201, response.status());
                context.put("orderId", response.jsonPath("$.data.id"));
            })
            .step("支付订单", context -> {
                Object orderId = context.get("orderId");
                ApiTestClient.ApiResponse response = client.post("/payments")
                        .jsonBody(Map.of("orderId", orderId))
                        .execute();
                assertEquals(200, response.status());
            })
            .step("查询最终状态", context -> {
                String orderId = context.require("orderId", String.class);
                ApiTestClient.ApiResponse response = client
                        .get("/orders/" + orderId)
                        .execute();
                assertEquals("PAID", response.jsonPath("$.data.status"));
            })
            .afterEach(context -> {
                // 每轮清理；即使某个步骤失败也会进入 afterEach。
                Object orderId = context.get("orderId");
                if (orderId != null) client.delete("/orders/" + orderId).execute();
            })
            .run();
}
```

`Context` 每轮重新创建，可通过 `put/get/require` 在关联步骤间传值；任一步断言失败会立即停止当前场景，执行 `afterEach` 后把失败交给 JUnit。

在 IDE 中点击 `orderPaymentScenario()` 就能整体 Run/Debug。反复执行时设置 VM option：

```text
-Dtesttools.repeat=10
```

仓库中的可运行示例为 `AssociatedApiScenarioExamples.newFeatureScenario()`。如果场景更适合完全用文件维护，也可以继续使用后面的 TestNG suite，其 `repeat` 会重复整个 YAML 用例列表。

## 工作区文件

- `environments/`：目标 URL、默认安全处理器和环境变量。
- `secrets/local-secrets.yaml`：仅本地密钥，默认被 `.gitignore` 排除。
- `fixtures/`：可共享 JSON 请求/响应模型数据，字段可直接增删。
- `cases/`：接口用例、步骤、变量提取与断言。
- `suites/`：用例集合，支持 `repeat` 和 `stopOnFailure`。
- `mocks/`：Mock 匹配与响应定义；运行中修改文件，下一次请求即生效。
- `workspace.yaml`：默认环境、Mock 端口及按接口选择安全算法的规则。

目前执行器只实现 `protocol: http`，字段省略时也按 HTTP 处理。YAML 调用用例若配置 gRPC/Dubbo 会明确报“不支持”；Mock Server 会忽略非 HTTP 定义，避免预留的 RPC 文件影响 HTTP 服务。协议字段和模型边界已经保留，RPC 适配器尚未实现。

所有 YAML 配置同时支持 `.yaml` 和 `.yml`。解析采用严格字段校验，字段名拼错会在加载时失败，而不是被静默忽略。

当前断言支持 `status`、`header`、`jsonPath`、`responseTime`；操作符支持 `equals`、`notNull`、`contains`、`greaterThan`、`lessThan`。

## 可选：新增 YAML/TestNG 集成用例

当接口场景已经稳定，需要文件共享、重复执行或组成批量回归套件时，可以使用 YAML/TestNG 层。通常只需要新增 YAML/JSON 文件，不需要编写 TestNG Java 类。`FileDrivenApiTest` 会扫描 `cases/`，并把每个 YAML 用例作为一条独立的 TestNG 测试执行。

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

### 6. YAML 用例也可以在 IDEA 或 VS Code 中单独运行

可以像 JUnit 单元测试一样，让一个 Java 测试方法对应一个 YAML 用例，然后单独点击 Run 或 Debug。项目提供：

- `ApiCaseTestSupport`：执行单条 YAML 用例并输出详细日志的基类。
- `api-test-runner/src/test/java/io/github/localtools/testtools/runnercli/IdeApiTestLauncher.java`：可直接运行的示例入口。

每新增一个希望在 IDE 中单独运行的用例，在 `IdeApiTestLauncher` 中增加一个方法：

```java
@Test(description = "Run cases/create-order.yaml")
public void createOrder() {
    runCase("create-order");
}
```

其中 `create-order` 对应 `test-workspace/cases/create-order.yaml`。不需要在方法里组装 HTTP 请求，测试数据仍只维护在 YAML/JSON 中。

运行和调试步骤：

1. 先启动真实目标服务；如果用例调用本项目的 Mock，则启动 `MockServerApplication` 或 Mock jar。
2. 在 IDEA 或 VS Code 中打开 `IdeApiTestLauncher.java`。
3. 点击某个方法左侧的 TestNG 图标，选择 **Run** 只运行该 YAML 用例，选择 **Debug** 调试该用例。
4. 可在测试方法、`ApiCaseTestSupport.runCase()`、自定义 `HttpSecurityHandler`、`TestCaseRunner` 或 `HttpExecutor` 中设置断点。

运行时 IDE 控制台和 TestNG 报告会直接显示：

- 用例名、环境和耗时。
- 每一步的 HTTP method、URL、请求 header/body。
- 响应状态、header/body 和耗时。
- 使用的签名处理器、验签结果和每条断言结果。
- 异常信息及完整 JSON 结果文件路径。

签名、Authorization、Cookie、API Key 等敏感 header 会显示为 `***`。请求和响应 body 默认最多输出 4000 个字符，可在 VM options 中调整，例如 `-Dtesttools.logBodyLimit=10000`；设置为 `-1` 表示不截断。

IDE 入口会从当前工作目录开始向上查找 `test-workspace/workspace.yaml`，因此从仓库根目录或 `api-test-runner` 模块运行都可以。使用其他工作区时，在 TestNG Run Configuration 的 VM options 中设置：

```text
-Dtesttools.workspace=/absolute/path/to/test-workspace
```

VS Code 需要启用支持 TestNG 的 Java 测试扩展。`IdeApiTestLauncher` 的类名特意不以 `Test`、`Tests` 或 `TestCase` 结尾，所以普通 `./mvnw test` 不会误调用本地接口；IDE 中显式运行不受影响。如果希望按业务拆分，可以新增其他继承 `ApiCaseTestSupport` 的 `*Launcher` 类，并按相同方式为每个 YAML 用例添加测试方法。

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

每个签名算法实现一个 `HttpSecurityHandler`，其 `id()` 是 YAML 中引用的算法名称。调用侧的处理器解析优先级为：

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

Mock 和响应后回调不使用调用侧的路由规则，应在各自定义中显式选择处理器：

```yaml
# mocks/order.yaml
securityHandler: orderV2Signature

afterResponse:
  - name: order-callback
    securityHandler: callbackHmacSignature
    request:
      method: POST
      url: http://127.0.0.1:8080/callback
```

这里允许请求接口、Mock 接口和回调接口使用不同算法。未配置时使用 `none`。

## 快速新增签名/验签算法

### 1. 先明确签名协议

实现前先和目标接口确认以下规则；调用侧和服务端只要有一项不一致，签名就会失败：

- 参与签名的字段：method、path、query、header、timestamp、nonce、body 中的哪些部分。
- query 使用原始顺序还是按 key 排序，使用编码后的值还是 URL decode 后的值。
- body 使用实际发送的原始字符串，还是解析 JSON 后按字段排序重新序列化。
- 字段之间是否使用换行、`&` 等分隔符，以及空 query/body 如何处理。
- 字符集、摘要算法、密钥格式和最终编码，例如 UTF-8、HMAC-SHA256、Hex 小写或 Base64。
- 签名放在哪个 header/query 字段，响应是否也需要验签。

推荐把“签名数据组装”和“加密计算”拆成两个私有方法。签名请求和 Mock 验签必须调用同一个组装方法，避免两份逻辑逐渐不一致。

### 2. 实现处理器

仓库已提供可直接运行的 `QueryBodyHmacSecurityHandler.java`。复制它并修改 `id()`、`buildSignData()` 与密码计算方法，就能快速支持新接口。该示例将排序后的 `queryString + bodyString` 直接拼接，再使用本地密钥计算 HMAC-SHA256：

```java
public final class QueryBodyHmacSecurityHandler implements HttpSecurityHandler {
    @Override
    public String id() {
        return "queryBodyHmacSha256";
    }

    @Override
    public void signRequest(SignContext context, MutableRequest request) {
        String signData = buildSignData(request.uri(), request.bodyText());
        request.header("X-App-Key", context.secret("appKey"));
        request.header("X-Signature", hmacSha256(context.secret("appSecret"), signData));
    }

    @Override
    public VerificationResult verifyMockRequest(SignContext context, RequestSnapshot request) {
        String signData = buildSignData(request.uri(), request.bodyText());
        String expected = hmacSha256(context.secret("appSecret"), signData);
        String actual = request.firstHeader("X-Signature");
        return secureEquals(expected, actual)
                ? VerificationResult.ok()
                : VerificationResult.failed("request signature mismatch");
    }

    private String buildSignData(URI uri, String bodyString) {
        String rawQuery = uri.getRawQuery();
        String queryString = rawQuery == null || rawQuery.isBlank()
                ? ""
                : Arrays.stream(rawQuery.split("&"))
                        .sorted()
                        .collect(Collectors.joining("&"));

        // 如果接口约定 queryString + bodyString 直接拼接，就不要自行增加分隔符。
        return queryString + (bodyString == null ? "" : bodyString);
    }

    private String hmacSha256(String secret, String signData) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(signData.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Cannot calculate HMAC-SHA256", error);
        }
    }

    private boolean secureEquals(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
```

需要的 import：

```java
import io.github.localtools.testtools.http.HttpModels.MutableRequest;
import io.github.localtools.testtools.http.HttpModels.RequestSnapshot;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Collectors;
```

上述示例使用 `URI.getRawQuery()`，即使用最终 URL 中已经编码的 query。若目标接口要求保持传入顺序，应移除 `.sorted()`；若要求先 URL decode、按 key/value 分别排序或排除某些参数，也只修改 `buildSignData()`。不要根据自己的习惯改变协议。

`request.bodyText()` 是工具最终发送的 UTF-8 请求体字符串。签名后不要再修改 body。对 JSON 签名且服务端要求原文完全一致时，建议使用 `fixtures/*.json` 固定字段顺序和格式。

### 3. 注册算法并配置密钥

在同目录的 `DefaultSecurityHandlers.java` 的 `create()` 方法中注册。示例处理器已经注册；复制出新处理器时再增加一行：

```java
handlers.add(new QueryBodyHmacSecurityHandler());
```

然后在 `test-workspace/secrets/local-secrets.yaml` 的目标 `secretRef` 下提供处理器读取的 key：

```yaml
secrets:
  order-service:
    appKey: local-app-key
    appSecret: local-app-secret
```

`context.secret("appSecret")` 从当前环境 `secretRef` 对应的密钥组取值；缺少字段会直接报错并写入失败结果。密钥名由处理器自行定义。

### 4. 选择算法并验证

将 `queryBodyHmacSha256` 配置到用例步骤、`workspace.yaml securityRules`、环境默认处理器、Mock 或 afterResponse 中，然后重新构建：

```bash
./mvnw clean package
```

仓库中的 `CodeFirstApiExamples.signedQueryAndBodyWithReusableHandler()` 会实际请求 `mocks/query-body-signed.yaml`。它同时演示了两个同名 query 参数、包含空格的编码值、JSON body、请求验签和响应验签，可在 Mock Server 启动后直接单独运行或调试。

建议至少验证以下场景：正确签名成功、修改 query 后失败、修改 body 后失败、错误密钥失败、空 query/body 的结果符合约定。请求和响应的敏感签名 header 在持久化结果中会被 `***` 遮盖。

一个处理器有四个方向：

- `signRequest`：调用服务或发送 afterResponse 前签名。
- `verifyResponse`：调用服务或 afterResponse 收到响应后验签。
- `verifyMockRequest`：Mock 收到请求后验签。
- `signMockResponse`：Mock 返回响应前签名。

接口提供了默认空实现，只覆盖实际协议需要的方向即可。若服务响应也签名，应使用与服务端完全相同的响应组装规则实现 `verifyResponse`；本地 Mock 要模拟该行为时同时实现 `signMockResponse`。完整的四方向示例见 `DemoHmacSecurityHandler.java`。

也支持把处理器放在独立 jar 中，通过 Java `ServiceLoader<HttpSecurityHandler>` 注册，核心代码无需修改。

## 常见问题与排查

### IDE 中找不到工作区

默认会从当前工作目录逐级向上查找 `test-workspace/workspace.yaml`。如果测试类从其他目录启动，在 JUnit/TestNG Run Configuration 的 VM options 中增加：

```text
-Dtesttools.workspace=/absolute/path/to/test-workspace
```

### Mock 返回 404 No mock matched

先检查请求的 method、path、已配置 query/header 和 JSON body 是否与 Mock 定义完全相等，再访问 `GET /__testtools/calls` 查看实际 URL、状态和命中结果。Mock 文件修改后不需要重启；文件名可用 `.yaml` 或 `.yml`。

### 签名不一致

在 `buildSignData()` 或用例的 `signWith` lambda 上打断点，同时核对：

- `getRawQuery()` 是 URL 编码后的最终 query，空格通常表现为 `+`。
- 同名 query 会保留；是否排序、是否排除签名字段必须遵守目标协议。
- `bodyText()` 是最终发送的 UTF-8 原文；JSON 字段顺序、空白和换行都可能影响签名。
- Hex/Base64、大小写、字符集、分隔符和密钥格式必须两端完全一致。

使用 `executeVerified()` 时验签失败会直接显示为 JUnit 失败；需要专门测试“验签应失败”的负向用例时使用 `execute()` 并断言 `response.verification()`。

### 回调没有发出

先看 `GET /__testtools/callbacks`。无效协议、缺失 request、连接失败、超时和重试耗尽都会记录为 `FAILED`，并带有错误原因；已完成记录还会写入 `results/callbacks/YYYY-MM-DD/`。原 Mock 响应先返回，回调结果不会反向改变它。

### YAML 加载失败

配置使用严格字段检查，错误信息会指出未知字段。重点检查缩进、字段拼写、文件引用，以及 `environment`、`secretRef`、`securityHandler` 名称是否存在。JSON fixture 中变量即使包含引号或换行也会作为字符串安全替换，不需要手工转义。

### 日志太长或包含业务敏感数据

签名、Authorization、Cookie、API Key 及敏感 query 会自动显示为 `***`，但框架无法推断普通业务字段是否敏感。请求和响应 body 默认各显示前 4000 个字符，可用 `-Dtesttools.logBodyLimit=1000` 调小，或设为 `-1` 不截断。不要在共享 fixture 或 Mock 响应中放入真实生产密钥和个人数据。

## 当前边界

- 已实现 HTTP 调用、HTTP Mock 和 HTTP afterResponse；gRPC/Dubbo 仅预留模型与 `protocol` 边界。
- Mock 适合本地开发的确定性桩，不包含录制回放、代理、复杂故障注入和高并发压测。需要这些能力时可评估 WireMock/MockServer 等成熟框架。
- Java DSL 当前每个 header 名只保存一个值；query 支持同名多值。
- JUnit 代码化用例使用 IDE/Maven 的测试报告与控制台日志；YAML/TestNG 用例额外持久化完整结构化执行结果。
- Mock 调用与回调管理接口在内存中保留最近 100 条；已完成回调另有文件记录。

## 为什么这样拆分

`ApiTestClient` 位于无测试框架依赖的 core，JUnit/TestNG 都可以调用；JUnit 负责代码化单用例，TestNG 只负责可选的 YAML 批量编排。Spring Boot 是独立 Mock 进程宿主，当前仅实现需要的 HTTP 匹配和回调能力；如果未来需要复杂录制回放或故障模拟，可以再把 WireMock 接到 HTTP 引擎内部。gRPC/Dubbo 将作为独立协议适配器加入，不改变现有 HTTP 用例。
