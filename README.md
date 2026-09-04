# Local API Test Tools

一个轻量、单机、文件驱动的 Java 接口测试与 Mock 工具。它运行在本地，不需要数据库或账号系统。

## 快速开始

要求：Java 21、curl。

~~~bash
./mvnw spring-boot:run
~~~

打开 <http://localhost:18080>。内置 Mock 服务监听 <http://127.0.0.1:19090>。

首次执行会把 Maven 下载到项目的 .mvn 目录。执行测试：

~~~bash
./mvnw test
~~~

默认工作区为 ./test-workspace，可通过环境变量修改：

~~~bash
TEST_TOOLS_WORKSPACE=/absolute/path/to/workspace ./mvnw spring-boot:run
~~~

## 工作区

- environments：目标 URL、默认安全处理器和环境变量。
- secrets/local-secrets.yaml：本地密钥，已被 .gitignore 排除。
- fixtures：可共享 JSON 请求数据。
- cases：测试用例。
- mocks：Mock 定义，文件修改后下一次请求立即生效。
- workspace.yaml：默认环境、Mock 端口和按接口选择签名算法的规则。

## 为不同接口选择不同签名算法

解析优先级：

1. 用例步骤的 securityHandler
2. workspace.yaml 中第一条匹配的 securityRules
3. 环境的 defaultSecurityHandler
4. none

建议优先用稳定的 operationId：

~~~yaml
securityRules:
  - operationId: createOrder
    handler: orderV2Signature
  - method: POST
    pathPattern: /legacy/**
    handler: legacyMd5Signature
~~~

单个步骤也能覆盖规则：

~~~yaml
- operationId: specialPayment
  method: POST
  path: /payments
  securityHandler: paymentRsaSignature
~~~

## 添加新的签名算法

新建 Spring Bean，实现 ApiSecurityHandler：

~~~java
@Component
public class OrderV2SecurityHandler implements ApiSecurityHandler {
    @Override
    public String id() {
        return "orderV2Signature";
    }

    @Override
    public void signRequest(SignContext context, MutableRequest request) {
        String secret = context.secret("orderSecret");
        String content = request.method()
                + request.uri().getRawPath()
                + request.bodyText();
        request.header("X-Signature", calculate(secret, content));
    }

    @Override
    public VerificationResult verifyResponse(
            SignContext context,
            RequestSnapshot request,
            ResponseSnapshot response) {
        return VerificationResult.ok();
    }
}
~~~

保存源码并重启应用后，Bean 的 id 就能在 YAML 中使用，无需修改注册表或执行器。

一个处理器有四个独立扩展点：

- signRequest：调用真实接口前签名。
- verifyResponse：收到真实接口响应后验签。
- verifyMockRequest：项目调用本地 Mock 时验证请求。
- signMockResponse：本地 Mock 返回前签名。

不需要的方向保留默认实现即可。完整示例见 DemoHmacSecurityHandler。

## 用例与 API

当前断言支持 status、header、jsonPath、responseTime；操作符支持
equals、notNull、contains、greaterThan、lessThan。

- GET /api/workspace：工作区摘要。
- POST /api/cases/{fileName}/run：执行用例。
- GET /api/mocks/calls：查看最近 50 次 Mock 调用。

当前是第一个可运行 MVP：完成多签名策略、文件数据、顺序用例、变量提取、断言以及本地 Mock 闭环。
