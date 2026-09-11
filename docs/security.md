# 签名与验签扩展

## 选择方式

- 只有某个 API 使用：优先在对应 JUnit 测试类中使用 `signWith` / `verifyWith`。
- 多个用例使用，或调用侧与 Mock 侧都需要：在 `project-security` 实现 `HttpSecurityHandler`。
- 不要把具体 API 的字段顺序和拼接规则放进 core。

core 提供 `HmacSha256.signHex(...)`、`signBase64(...)`、返回原始字节的 `sign(...)`，以及用于校验认证值的 `ConstantTime.equalsUtf8(...)`。正式的可复用处理器应调用这些方法，避免重复处理算法名称、UTF-8、输出编码和安全比较。`SigningExamples` 仍保留一份直接使用 `Mac` 的本地实现，用于演示单个用例完全自包含时如何调试和修改。

## 用例内签名

假设规则为：按顺序读取 Header 中的 `tranId`、`timestamp` 和 JSON body 中的 `name`，拼成：

```text
tranId111timestamp222name333
```

```java
.header("tranId", "111")
.header("timestamp", "222")
.jsonBodyFile(globalJson("header-body-request.json"))
.signWith(request -> {
    String signData = headerBodySignData(request);
    String signature = hmacSha256(secret("local", "appSecret"), signData);
    request.header("X-App-Key", secret("local", "appKey"));
    request.header("X-Signature", signature);
})
```

接口专属的组装方法同样放在测试类中：

```java
private String headerBodySignData(MutableRequest request) {
    try {
        String tranId = requiredHeader(request, "tranId");
        String timestamp = requiredHeader(request, "timestamp");
        JsonNode body = workspace().jsonMapper().readTree(request.body());
        return "tranId" + tranId
                + "timestamp" + timestamp
                + "name" + body.path("name").asText();
    } catch (IOException error) {
        throw new IllegalArgumentException("Invalid JSON request body", error);
    }
}
```

应先明确并测试以下协议细节：

- 使用原始 query 还是 URL decode 后的值。
- query 是否排序、同名参数如何处理。
- Header 名是否忽略大小写，缺失值是否报错。
- body 使用原始字节还是重新序列化后的 JSON。
- 字段间是否有名称、分隔符、换行或长度前缀。
- 字符集、摘要算法和输出格式，例如 UTF-8、HMAC-SHA256、hex 小写。

## 可复用处理器

在 `project-security/src/main/java/.../project/security/` 新建：

```java
public final class OrderApiSecurityHandler implements HttpSecurityHandler {
    @Override
    public String id() { return "orderApiV1"; }

    @Override
    public void signRequest(SignContext context, MutableRequest request) {
        String content = buildRequestSignData(request);
        request.header("X-Signature",
                HmacSha256.signHex(context.secret("appSecret"), content));
    }

    @Override
    public VerificationResult verifyMockRequest(SignContext context, RequestSnapshot request) {
        String expected = HmacSha256.signHex(
                context.secret("appSecret"), buildMockSignData(request));
        return ConstantTime.equalsUtf8(expected, request.firstHeader("X-Signature"))
                ? VerificationResult.ok()
                : VerificationResult.failed("request signature mismatch");
    }
}
```

在下面的 ServiceLoader 文件增加完整类名：

```text
project-security/src/main/resources/
META-INF/services/io.github.mengdlmole.testtools.security.HttpSecurityHandler
```

JUnit 使用：

```java
.security(securityHandler("orderApiV1"), signContext("local"))
```

Mock 使用：

```yaml
securityHandler: orderApiV1
```

每个处理器必须有单元测试，至少覆盖正确签名、body 被修改、Header 被修改和缺少密钥。

如果协议发送 appKey，Mock 验签也应验证它，不能只验证摘要。时间戳参与签名时，还应根据真实协议决定是否校验格式和允许的时间偏差；示例 `demoHmacSha256` 要求 epoch 秒并允许前后 5 分钟。

## YAML runner 选择优先级

1. YAML step 的 `securityHandler`
2. `yaml-runner.yaml` 中第一条匹配的 `securityRules`
3. 环境的 `defaultSecurityHandler`
4. `none`

```yaml
securityRules:
  - operationId: createOrder
    handler: orderApiV1
  - method: POST
    pathPattern: /legacy/**
    handler: legacyHmac
```
