# Spring Boot Mock Server

## 启动

```bash
./mvnw -pl mock-server -am package
java -jar mock-server/target/mock-server.jar --workspace test-workspace
```

只监听 `127.0.0.1`，端口来自 `test-workspace/mock-server.yaml`。

## 新增 Mock

在 `test-workspace/mocks/<名称>.yaml` 新建：

```yaml
name: 创建订单
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

内联 `body` 默认添加 `Content-Type: application/json`。使用文件时需要显式指定媒体类型：

```yaml
response:
  status: 200
  headers:
    Content-Type: application/json
  bodyFile: fixtures/mock/order-response.json
```

`body` 与 `bodyFile` 不能同时配置。Mock Server 每次重新读取文件时都会校验 HTTP Mock 的名称、request、path、response，以及 callback 的 request、URL 和 body 来源；配置错误会返回明确的执行错误，不会静默选择其中一个字段。

## 请求验签和响应签名

```yaml
securityHandler: orderApiV1
```

对应处理器由 `project-security` 注册。Mock 侧会调用 `verifyMockRequest`，验证成功后调用 `signMockResponse`。

## 响应后回调

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

原 Mock 响应完成后才异步提交回调；回调失败不会改变已经返回的响应。支持 `${mock.name}`、`${request.method}`、`${request.path}`、`${request.body}`、`${callback.id}` 和工作区变量。

## 管理接口

- `GET /__testtools/health`
- `GET /__testtools/calls`
- `GET /__testtools/callbacks`

最近 100 条记录保存在内存；完成的回调写入 `test-workspace/results/callbacks/`。
