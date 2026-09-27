# 架构与命名约定

## 设计目标

- 本地单人使用，配置和预制数据通过文件共享。
- JUnit 代码优先，单个 API 用例可直接 Run、Debug。
- 签名协议允许按 API 定制，不强迫所有接口使用同一套拼接规则。
- Mock Server 独立运行，不侵入被测服务。
- HTTP 是当前实现；未来 RPC 通过新适配模块扩展。

## 为什么叫 core，不叫 common

保留 `test-tools-core`。

`core` 表示稳定、被其他模块依赖的核心机制和扩展契约；`common` 通常无法说明代码为什么公共，容易逐渐收纳工具类、业务模型和临时逻辑。任何新代码进入 core 前都应满足：至少两个功能模块需要它，并且它不依赖 JUnit、Spring Boot 或 Mock 领域模型。

## 模块职责

| 模块 | 可以包含 | 不应包含 |
| --- | --- | --- |
| `test-tools-core` | 简单 HTTP 客户端、请求响应模型、日志脱敏、Mock 安全 SPI、HMAC 等纯密码学能力、环境/密钥/fixture 文件访问 | JUnit 注解、Spring MVC、Mock 定义、项目签名原文组装规则 |
| `project-security` | Mock 请求、响应或回调使用的具体 API 安全协议及单元测试 | JUnit 用例签名、通用 HTTP 执行、Mock 控制器、测试编排 |
| `api-tests` | JUnit 支持类、显式请求组装和签名、项目 API 测试 | 自定义测试 DSL、Mock 实现 |
| `mock-server` | HTTP Mock 定义、匹配、响应、回调、管理接口 | JUnit API 用例、RPC 适配 |

依赖方向保持单向：功能模块依赖 core，`project-security` 只实现 core SPI，core 不反向依赖任何功能模块。

`api-tests` 内部固定使用 `support`、`examples`、`cases/<业务域>` 三层结构。公共支撑代码不能与真实测试混放；示例不能充当项目业务测试；真实测试不能反向依赖示例类。

`mock-server` 的 Java 源码全部是测试桩框架，不是具体接口用例，按职责组织：

```text
mock/
├── MockServerApplication.java       Spring Boot 入口和基础 Bean
├── admin/                           健康检查、调用与回调记录
├── callback/                        响应完成后的异步回调
├── config/                          Mock 配置及工作区视图
├── engine/                          请求匹配和响应生成
├── model/                           YAML 定义模型
└── web/                             HTTP 控制器和异常响应
```

这里不设 `common` 包：需要命名为 `common` 的类通常还没有找到明确职责。具体接口 Mock
以 YAML 形式放在 `test-workspace/mocks/cases/<业务域>`；工具自身示例放在
`test-workspace/mocks/examples`。两者由 Mock Server 递归加载，但目录语义不可混用。

## 包和类型命名

- Maven 坐标统一使用 `io.github.mengdlmole`，Java 根包统一使用 `io.github.mengdlmole.testtools`；其中 `mengdlmole` 对应 GitHub 用户名并按 Java 约定全部小写。
- 源码目录必须与包名一致，例如 `io.github.mengdlmole.testtools.http` 对应 `src/main/java/io/github/mengdlmole/testtools/http/`。
- `http` 中的 `ApiTestClient`、`ApiRequest`、`ApiResponse` 是 JUnit 用例 API；`http.transport` 是 HTTP 执行器和 Mock 安全扩展使用的底层传输模型，普通用例不直接依赖。
- 可持久化定义使用 `Definition`，例如 `MockDefinition`。
- 本地配置使用 `Config`，例如 `WorkspaceConfig`、`MockServerConfig`。
- 执行动作用 `Engine`、`Dispatcher`，例如 `HttpMockEngine`、`HttpCallbackDispatcher`。
- 文件视图用 `Workspace`：`TestWorkspace` 是安全文件访问，`MockWorkspace` 是 Mock 领域视图。
- 示例测试类使用 `Examples` 后缀，避免 Maven 默认测试阶段误调用本地 API。
- 框架自身的单元测试使用 `Test` 后缀并由 Maven 自动执行。

避免 `Utils`、`Common`、`Manager`、`Models`、`FileDriven` 等无法表达领域的名称。

## 工作区配置边界

- `workspace.yaml`：所有模块共享的默认环境和变量。
- `mock-server.yaml`：仅 Mock Server 使用的端口等配置。
- `environments/`、`secrets/`：调用侧和 Mock 侧可共享。
- `fixtures/api-tests/global/`：多个 JUnit API 测试类共享的 JSON。
- `fixtures/api-tests/cases/<case>/`：一个测试类或关联场景拥有的 JSON。
- `fixtures/mock/<业务域>/`：多个 Mock 定义引用的响应体或回调请求体。
- `mocks/examples/`：工具自带的演示接口，与 `api-tests` 示例配套。
- `mocks/cases/<业务域>/`：项目实际使用的 Mock 接口定义。

JUnit 和 Mock Server 通过 core 的 `WorkspaceLocator` 使用同一套工作区定位规则，并通过 `TestWorkspace.environmentContext(...)` 使用相同的变量覆盖顺序。HTTP URL 与 query 的基础组装同样由 core 统一；JUnit 用例使用普通 Java/JUnit 显式组装请求和签名，Mock Server 才通过安全 SPI 选择可复用处理器。具体 API 的签名规则不进入 core。

新增协议时建议增加独立模块，例如 `dubbo-test-adapter`，实现自己的客户端、用例模型或 Mock 适配；不要向 HTTP 类中持续增加 `if (protocol)` 分支。
