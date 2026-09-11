# 可选 TestNG/YAML Runner

JUnit 是默认入口。只有稳定场景需要脱离 Java 文件维护、批量执行或生成 TestNG 报告时才使用 YAML runner。

## 新增用例

`test-workspace/cases/create-order.yaml`：

```yaml
name: 创建订单
environment: local
variables:
  productId: P1001

steps:
  - name: 调用创建接口
    operationId: createOrder
    protocol: http
    method: POST
    path: /orders
    headers:
      X-Tenant-Id: "${tenantId}"
    caseBodyFile: request.json
    extract:
      orderId: $.data.id
    assertions:
      - type: status
        expected: 201
      - type: jsonPath
        path: $.data.status
        expected: CREATED
```

用例级 JSON body 放在 `test-workspace/fixtures/cases/create-order/request.json`；全局 JSON body 使用 `globalBodyFile` 并放在 `fixtures/global/`。`body`、`globalBodyFile` 和 `caseBodyFile` 会默认添加 `Content-Type: application/json`。

`bodyFile` 用于读取工作区内的原始文本或其他自定义格式，不假定内容为 JSON，应在 `headers` 中明确设置相应的 `Content-Type`。四种 body 来源只能选择一个。

文件在执行前会进行基础结构校验：case 必须有名称和至少一个具名 step，step 必须有 path；suite 必须包含至少一个非空 case 名。校验失败也会由 runner 记录为失败结果。

## 新增 suite

`test-workspace/suites/order-regression.yaml`：

```yaml
name: 订单回归
cases:
  - create-order
  - query-order
stopOnFailure: true
repeat: 3
```

## 执行

```bash
./mvnw -pl yaml-test-runner -am package

java -jar yaml-test-runner/target/yaml-test-runner.jar \
  --workspace test-workspace --case create-order

java -jar yaml-test-runner/target/yaml-test-runner.jar \
  --workspace test-workspace --suite order-regression
```

省略 `--case` 和 `--suite` 会执行所有 case，两者不能同时使用。TestNG 报告写入 `results/testng/`，结构化请求、响应、断言和异常写入按日期组织的 JSON。

## IDE 单用例

在 `YamlCaseIdeLauncher` 增加：

```java
@Test
void createOrder() {
    runCase("create-order");
}
```

然后从 IDEA 或支持 TestNG 的 VS Code Java 测试扩展中 Run/Debug 该方法。使用其他工作区时添加 VM option：

```text
-Dtesttools.workspace=/absolute/path/to/test-workspace
```

`YamlSuiteTest` 位于 `src/main`，因为它是可执行 jar 在运行时交给 TestNG 的入口；项目 API 用例仍然只放在 `api-tests/src/test`。

## 支持的断言

- 类型：`status`、`header`、`jsonPath`、`responseTime`
- 操作符：`equals`、`notNull`、`contains`、`greaterThan`、`lessThan`
