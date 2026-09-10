# 架构与命名约定

## 设计目标

- 本地单人使用，配置和预制数据通过文件共享。
- JUnit 代码优先，单个 API 用例可直接 Run、Debug。
- 签名协议允许按 API 定制，不强迫所有接口使用同一套拼接规则。
- Mock Server 独立运行，不侵入被测服务。
- HTTP 是当前实现；未来 RPC 通过新适配模块扩展。

## 为什么叫 core，不叫 common

保留 `test-tools-core`。

`core` 表示稳定、被其他模块依赖的核心机制和扩展契约；`common` 通常无法说明代码为什么公共，容易逐渐收纳工具类、业务模型和临时逻辑。任何新代码进入 core 前都应满足：至少两个功能模块需要它，并且它不依赖 JUnit、TestNG、Spring Boot、YAML case 或 Mock 领域模型。

## 模块职责

| 模块 | 可以包含 | 不应包含 |
| --- | --- | --- |
| `test-tools-core` | HTTP DSL、请求响应模型、日志脱敏、签名 SPI、HMAC 等纯密码学能力、环境/密钥/fixture 文件访问 | JUnit/TestNG 注解、Spring MVC、YAML case、Mock 定义、项目签名原文组装规则 |
| `project-security` | 当前项目或指定 API 的签名/验签实现及单元测试 | 通用 HTTP 执行、Mock 控制器、测试编排 |
| `api-tests` | JUnit 支持类、关联场景 DSL、项目 API 测试 | Mock 实现、YAML 执行引擎 |
| `yaml-test-runner` | YAML case/suite 模型、签名选择规则、TestNG 入口、结果持久化 | Spring Boot Mock、项目业务签名实现 |
| `mock-server` | Mock 定义、匹配、响应、回调、管理接口 | JUnit API 用例、YAML 测试编排 |

依赖方向保持单向：功能模块依赖 core，`project-security` 只实现 core SPI，core 不反向依赖任何功能模块。

## 包和类型命名

- 可持久化定义使用 `Definition`：`YamlCaseDefinition`、`YamlStepDefinition`、`MockDefinition`。
- 本地配置使用 `Config`：`WorkspaceConfig`、`YamlRunnerConfig`、`MockServerConfig`。
- 执行动作用 `Runner`、`Engine`、`Dispatcher`：`YamlCaseRunner`、`HttpMockEngine`、`HttpCallbackDispatcher`。
- 文件视图用 `Workspace`：`TestWorkspace` 是安全文件访问，`YamlWorkspace` 和 `MockWorkspace` 是领域视图。
- 示例测试类使用 `Examples` 后缀，避免 Maven 默认测试阶段误调用本地 API。
- 框架自身的单元测试使用 `Test` 后缀并由 Maven 自动执行。

避免 `Utils`、`Common`、`Manager`、`Models`、`FileDriven` 等无法表达领域的名称。

## 工作区配置边界

- `workspace.yaml`：所有模块共享的默认环境和变量。
- `mock-server.yaml`：仅 Mock Server 使用的端口等配置。
- `yaml-runner.yaml`：仅 YAML runner 使用的签名路由等配置。
- `environments/`、`secrets/`、`fixtures/`：调用侧和 Mock 侧可共享。

新增协议时建议增加独立模块，例如 `dubbo-test-adapter`，实现自己的客户端、用例模型或 Mock 适配；不要向 HTTP 类中持续增加 `if (protocol)` 分支。
