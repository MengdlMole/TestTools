# Mock 定义目录

这里存放 Mock Server 在本地提供的 HTTP 测试桩。Java 框架实现位于
`mock-server/src/main/java`，不要在本目录放 Java 公共类。

- `examples/`：随工具提供的可运行示例，与 `api-tests/.../examples` 对应。
- `cases/<业务域>/`：项目真实使用的 Mock 接口，例如
  `cases/order/create-order.yaml`。

一个 YAML 文件描述一个接口行为。文件使用 `kebab-case` 命名；同一业务域的接口放在同一
子目录。Mock Server 会递归加载本目录下所有 `.yaml` 和 `.yml` 文件，修改后无需重启。

不要创建含义宽泛的 `common/` 目录。多个接口共用的响应数据放到
`test-workspace/fixtures/mock/`，共用的签名或验签代码放到 `project-security`。
