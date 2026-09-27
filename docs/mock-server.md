# Spring Boot Mock Server

## 启动

```bash
./mvnw -pl mock-server -am package
java -jar mock-server/target/mock-server.jar --workspace test-workspace
```

只监听 `127.0.0.1`，端口来自 `test-workspace/mock-server.yaml`。

```yaml
port: 19090
reloadIntervalMs: 500
maskSensitiveData: true
```

`reloadIntervalMs` 是请求触发自动检查的最小间隔，不是后台轮询频率。没有请求时不会产生文件扫描。
`maskSensitiveData` 控制 `/__testtools/calls` 中是否隐藏 URI 用户名/密码和敏感 query，默认
开启。只有本地排查确实需要原文时才临时关闭。

## 新增 Mock

真实业务测试桩放在 `test-workspace/mocks/cases/<业务域>/`，例如
`test-workspace/mocks/cases/order/create-order.yaml`：

```yaml
name: 创建订单
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
    productId: P1001
    quantity: 2

response:
  status: 201
  delayMs: 50
  body:
    data:
      id: mock-order-001
      status: CREATED
```

匹配规则：已配置的 method、path、query、Header 和 JSON body 必须匹配；未配置的字段不参与。多个定义匹配时 `priority` 数值较小者优先，默认 100。

Mock Server 递归加载 `mocks/` 下所有 `.yaml` 和 `.yml` 文件，因此可以按业务域继续分目录：

```text
test-workspace/mocks/
├── examples/                       工具自带的可运行示例
└── cases/                           项目真实测试桩
    ├── order/
    │   ├── create-order.yaml
    │   └── query-order.yaml
    └── payment/
        └── pay-order.yaml
```

一个文件描述一个接口行为，文件使用 `kebab-case` 命名。不要建立含义模糊的
`mocks/common/`：共享响应文件放在 `test-workspace/fixtures/mock/`，共享验签代码放在
`project-security`。Java 框架源码与接口定义不混放。

内联 `body` 默认添加 `Content-Type: application/json`。使用文件时需要显式指定媒体类型：

```yaml
response:
  status: 200
  headers:
    Content-Type: application/json
  bodyFile: fixtures/mock/order/order-response.json
```

`body` 与 `bodyFile` 不能同时配置。加载 catalog 时会完整校验 HTTP Mock 的名称、request、
path、response、重复名称、HTTP method、状态码、Header，以及 callback 的 request、绝对
HTTP/HTTPS URL 和 body 来源。执行参数的范围为：

- 响应 `delayMs`：0～60,000 ms。
- callback `delayMs`：0～3,600,000 ms。
- callback `timeoutMs`：1～60,000 ms。
- `retry.maxAttempts`：1～10。
- `retry.intervalMs`：0～60,000 ms。

超出范围或格式非法的定义不会进入活跃 catalog；热加载失败时继续使用上一版。

## 原子热加载

Mock Server 启动时会完整加载第一版 catalog；启动配置错误时直接终止，避免启动一个不可用的服务。运行期间，Mock 请求和 catalog 状态查询会触发检查，但至多每隔 `reloadIntervalMs` 执行一次：

1. 读取并校验全部 Mock YAML。
2. 解析当前环境、变量和密钥。
3. 把 response、callback 引用的 `bodyFile` 一并读入不可变 catalog。
4. 计算 SHA-256 指纹；内容确实变化且全部有效时，一次性替换当前 catalog。

加载失败不会影响正在使用的版本。修正文件后下一次自动检查即可生效，也可以调用
`POST /__testtools/reload` 立即重载。

## 请求验签和响应签名

```yaml
securityHandler: orderApiV1
```

对应处理器由 `project-security` 注册。Mock 侧会调用 `verifyMockRequest`，验证成功后调用 `signMockResponse`。

## 响应后回调

```yaml
afterResponse:
  - name: order-created-callback
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

原 Mock 响应完成后才异步提交回调；回调失败不会改变已经返回的响应。支持 `${mock.name}`、`${request.method}`、`${request.path}`、`${request.body}`、`${callback.id}` 和工作区变量。

## 管理接口

- `GET /__testtools/health`
- `GET /__testtools/calls`
- `GET /__testtools/callbacks`
- `GET /__testtools/catalog`：当前版本、Mock 数量、检查时间及最近加载错误
- `POST /__testtools/reload`：立即尝试原子重载并返回 catalog 状态

最近 100 条记录保存在内存；完成的回调写入 `test-workspace/results/callbacks/`。

## 自动化集成测试

`./mvnw verify` 使用 Maven Failsafe 启动随机端口的真实 Spring Boot 服务，验证 HTTP
匹配、响应、响应完成后的 callback、调用记录和 catalog 管理接口。`*Test` 是快速单元测试，
`*IT` 是需要完整应用上下文和本地端口的集成测试。
