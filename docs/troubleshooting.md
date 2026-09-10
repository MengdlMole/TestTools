# 常见问题与当前边界

## IDE 找不到工作区

工具会从当前目录逐级向上查找 `test-workspace/workspace.yaml`。仍无法找到时设置：

```text
-Dtesttools.workspace=/absolute/path/to/test-workspace
```

## Mock 返回 404

检查 method、path、query、Header、JSON body 和 `enabled`。调用 `GET /__testtools/calls` 查看实际请求和命中结果。

## 签名不一致

逐项打印或断言签名原文，重点检查字段顺序、URL 编码、Header 大小写、原始 body 与重新序列化 JSON、字符集、摘要输出格式。不要只比较最终摘要。

## 回调没有发出

调用 `GET /__testtools/callbacks`，检查状态、尝试次数和错误；完成记录位于 `results/callbacks/`。

## YAML 加载失败

YAML 使用严格字段检查，拼错字段会直接失败。检查缩进、字段名称、环境、密钥引用、签名处理器 ID 和 fixture 路径。

## 日志与敏感数据

签名、Authorization、Cookie、API Key 及敏感 query 会自动显示为 `***`。body 默认最多显示 4000 字符，可用 `-Dtesttools.logBodyLimit=1000` 调整，`-1` 表示不截断。不要在共享 fixture 中放生产密钥和个人数据。

## 当前边界

- 已实现 HTTP 调用、HTTP Mock 和 HTTP afterResponse。
- gRPC/Dubbo 尚未实现；后续应增加独立协议适配模块。
- Mock 不包含录制回放、代理、复杂故障注入和压测；需要时评估 WireMock 或 MockServer。
- Java DSL 的同名 Header 当前只保存一个值，query 支持同名多值。
- JUnit 使用 IDE/Maven 日志与报告；YAML runner 额外持久化结构化结果。
