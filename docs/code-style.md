# 编码规范与格式化

## 项目基线

Java 代码遵循 Google Java Style。排版不依赖个人 IDE 配置，而由 Maven 中固定版本的工具统一执行：

- Spotless + google-java-format：自动处理代码缩进、换行和 import。
- Checkstyle：检查命名、import、花括号、Javadoc 结构以及容易引入缺陷的 Java 写法。
- EditorConfig：统一 UTF-8、LF、文件末尾换行、空格缩进和行尾空白。

格式化器是 Java 代码排版的唯一准则。Javadoc 采用比 Google Java Style 更严格的项目约定：
Spotless 会把单行 Javadoc 自动展开为多行，Checkstyle 负责校验结构；为了避免格式化器重新压回单行，
google-java-format 的 Javadoc 内容重排已关闭。

## Javadoc 约定

Google Java Style 允许短 Javadoc 写成单行，但本项目为了长期维护和后续补充标签，统一使用多行格式：

```java
/**
 * Shared URL and query assembly for HTTP-based test runners.
 */
final class HttpRequestUriBuilder {
}
```

不要写成：

```java
/** Shared URL and query assembly for HTTP-based test runners. */
```

### 类、接口和 record

公共扩展点和非显而易见的公共类型需要说明职责、边界和线程安全等重要约束：

```java
/**
 * Builds HTTP request URIs without owning transport execution.
 *
 * @since 0.2.0
 */
public final class HttpRequestUriBuilder {
}
```

- `@since` 表示该 API 首次出现的项目版本，稳定的公共 API 建议填写，例如 `0.2.0`；不要每次修改都更新。
- record 组件使用类型 Javadoc 中的 `@param componentName` 描述。
- 泛型参数写成 `@param <T>`。
- 本项目默认不写 `@author`：Git 历史能准确记录多人贡献，类上的单一作者很容易失真。如果所属团队强制要求，只能放在类型上，使用真实且长期稳定的团队名；不要放在方法和字段上。

### 方法和构造器

公共方法的 Javadoc 描述调用者需要知道的契约，而不是逐句翻译实现：

```java
/**
 * Builds an absolute URI and preserves the iteration order of query parameters.
 *
 * @param baseUrl base URL used when {@code pathOrUrl} is relative
 * @param pathOrUrl absolute URL or path relative to {@code baseUrl}
 * @param query query parameters to append; may be {@code null}
 * @return assembled request URI
 * @throws IllegalArgumentException if {@code pathOrUrl} is blank
 */
public static URI build(
        String baseUrl, String pathOrUrl, List<QueryParameter> query) {
    // ...
}
```

- 每个参数（含泛型参数）对应一个 `@param`，顺序必须和声明一致。
- 非 `void` 方法说明 `@return`；明显访问器可以不写整段 Javadoc。
- 使用 `@throws` 描述调用者可预期的异常条件，不罗列不可能处理的内部异常。
- 弃用 API 必须同时添加 `@Deprecated` 和 `@deprecated`，后者说明原因、替代 API 和迁移方式。
- 重写方法通常依赖父类型文档，不重复书写；确需补充差异时使用 `{@inheritDoc}`。

### 字段、常量和局部变量

Javadoc 可以修饰字段，但字段没有 `@param` 或 `@return` 标签：

```java
/**
 * Default timeout applied when a request does not override it.
 */
public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
```

公共常量在含义、单位或取值范围不直观时应写 Javadoc；私有字段、方法参数和局部变量不写 Javadoc，也不要给每个变量添加复述名称的注释。优先改进命名，确需解释实现原因时使用普通 `//` 注释。

### 标签顺序

项目采用以下顺序，不存在的标签直接省略：

```text
@author
@version
@param
@return
@throws / @exception
@see
@since
@deprecated
```

其中项目通常只使用 `@param`、`@return`、`@throws`、`@see`、`@since` 和 `@deprecated`。

### 快速生成

IDEA：

1. 在类、方法或字段声明上一行输入 `/**`，按 `Enter`。方法会自动生成匹配签名的 `@param`、`@return` 和 `@throws`。
2. 也可把光标放在声明名上，按 `Alt+Enter`（macOS 为 `Option+Enter`），选择 **Add Javadoc**。
3. 签名变化后，使用 **Find Action** 搜索 **Fix Doc Comment**，自动补齐或修正标签；IDEA 默认快捷键为 `Ctrl+Shift+A`，macOS 为 `Command+Shift+A`。

VS Code：

1. 安装 **Extension Pack for Java** 或 **Language Support for Java by Red Hat**。
2. 在声明上一行输入 `/**` 并按 `Enter`，从补全列表生成 Javadoc 骨架。
3. VS Code 没有项目统一绑定的专用 Javadoc 快捷键；可在 **Keyboard Shortcuts** 中为 Java 的相关补全或代码操作绑定个人快捷键。

IDE 只生成骨架，说明文字仍需要开发者填写。完成后运行 `./mvnw spotless:apply` 和 `./mvnw verify`；前者统一排版，后者检查标签与方法签名是否一致。

### 通用规则

- 对外扩展点、公共类型以及语义不直观的公共方法应写 Javadoc；显而易见的访问器和重写方法可省略。
- 摘要是完整句子，以句号结尾。说明接口契约和设计意图，不复述类名或方法名。
- 每个标签必须有有效说明。
- 标签组前保留一个空行；续行保持四个空格缩进。
- 公共常量只有在含义不直观时使用 Javadoc。私有字段、参数和局部变量不使用 Javadoc；优先使用清晰命名，确需解释时用 `//` 说明“为什么”。

`./mvnw spotless:apply` 会自动修复单行形式；Checkstyle 会校验摘要、段落、标签顺序和标签内容。
是否需要为某个 API 补充文档仍需代码评审判断，避免为了通过检查生成无价值注释。

## 日常命令

修改 Java 代码后自动格式化：

```bash
./mvnw spotless:apply
```

仅检查格式，不修改文件：

```bash
./mvnw spotless:check
```

执行测试、格式检查和编码规范检查：

```bash
./mvnw verify
```

`verify` 是提交前的标准检查。格式不正确或 Checkstyle 违规都会让构建失败，并在控制台给出文件和行号。

个人机器可选安装 Spotless 的 pre-push hook：

```bash
./mvnw spotless:install-git-pre-push-hook
```

该 hook 只写入本地 `.git/hooks`，不会随仓库共享，因此项目仍以 `./mvnw verify` 作为统一门禁。

## IDEA 和 VS Code

IDE 应启用 EditorConfig 支持。保存时可以使用 IDE 的格式化能力，但提交前仍以 `./mvnw spotless:apply` 的结果为准，从而避免不同 IDE 插件版本产生差异。

如需在 IDE 中安装 google-java-format 插件，应选择 Google Style；插件只用于即时反馈，Maven 中固定的版本才是最终判定标准。

## 规则调整原则

- 新规则必须能自动检查，并且对当前所有模块一致生效。
- Java 代码排版只配置在 Spotless 中；Javadoc 的项目级结构约定配置在 Checkstyle 中。
- 不因单个用例关闭全局规则；确有例外时，优先缩小到具体文件或代码行，并说明原因。
- 升级 Spotless、google-java-format 或 Checkstyle 时，应单独提交格式化变化并执行完整 `./mvnw verify`。
