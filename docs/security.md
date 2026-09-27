# 签名与验签

## 两种使用场景

签名代码按使用方分开：

- JUnit API Test：在测试类中显式组装待签名字符串并写入 Header，不使用安全处理器或回调 DSL。
- Mock Server：YAML 不能表达任意 Java 签名协议，因此使用 `project-security` 中的 `HttpSecurityHandler` 完成验签、响应签名和 callback 签名。

不要把具体 API 的字段顺序和拼接规则放进 core。

## JUnit 用例内签名

假设规则为：按顺序读取 Header 中的 `tranId`、`timestamp` 和 JSON body 中的 `name`，拼成：

```text
tranId111timestamp222name333
```

完整过程应在发送前可见：

```java
ObjectNode body = globalJsonObject("header-body-request.json");
body.put("name", 333);

String tranId = "111";
String timestamp = "222";
ApiRequest request = client.post("/signed/header-body");
request.header("tranId", tranId);
request.header("timestamp", timestamp);
request.jsonBody(body);

// /signed/header-body 的服务端验签协议规定以下字段名称、顺序和拼接方式。
String contentToSign = "tranId" + tranId
        + "timestamp" + timestamp
        + "name" + body.path("name").asText();
String signature = hmacSha256(secret("local", "appSecret"), contentToSign);

request.header("X-App-Key", secret("local", "appKey"));
request.header("X-Signature", signature);

ApiResponse response = client.send(request);
```

`ApiRequest` 是当前测试创建、稍后发送的请求，不是框架回调参数。HTTP method、path、Header 或 body 是否参与签名，完全取决于服务端接口协议。调用 `send` 之前可以在断点中直接查看：

- `request.method()`：最终 HTTP 方法。
- `request.uri()`：已包含 query 的最终 URI。
- `request.header("name")`：当前 Header 值。
- `request.bodyText()`：最终发送的 UTF-8 body；仅在接口协议要求时加入签名原文。

应先明确并测试以下协议细节：

- 使用原始 query 还是 URL decode 后的值。
- query 是否排序、同名参数如何处理。
- Header 名是否忽略大小写，缺失值是否报错。
- body 使用原始文件字节还是修改后重新序列化的 JSON。
- 字段间是否有名称、分隔符、换行或长度前缀。
- 字符集、摘要算法和输出格式，例如 UTF-8、HMAC-SHA256、hex 小写。

core 提供 `HmacSha256.signHex(...)`、`signBase64(...)` 和返回原始字节的 `sign(...)`。接口协议特殊或希望完全自包含时，也可以直接在测试类中使用 JDK `Mac`；`SigningExamples` 同时演示了算法选择、签名原文组装和响应签名断言。

## Mock 可复用处理器

Mock Server 需要通过配置选择协议时，在 `project-security/src/main/java/.../project/security/` 新建处理器：

```java
public final class OrderApiSecurityHandler implements HttpSecurityHandler {
    @Override
    public String id() {
        return "orderApiV1";
    }

    @Override
    public VerificationResult verifyMockRequest(
            SignContext context, RequestSnapshot request) {
        String signData = buildSignData(request);
        String expected = HmacSha256.signHex(
                context.secret("appSecret"), signData);
        return ConstantTime.equalsUtf8(
                expected, request.firstHeader("X-Signature"))
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

Mock 使用：

```yaml
securityHandler: orderApiV1
```

每个处理器应有独立单元测试，至少覆盖正确签名、body 被修改、Header 被修改和缺少密钥。

`verifyMockRequest(...)` 没有默认放行实现，每个处理器都必须明确实现验签。只有 `NoSecurityHandler` 会显式允许不验签的 Mock。
