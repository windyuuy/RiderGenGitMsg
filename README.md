# RiderGenGitMsg

JetBrains Rider 插件：根据 **已暂存改动**（`git diff --cached`）调用 **DeepSeek** 生成 Git 提交信息，并写入 Commit 面板的提交信息输入框。

- 固定使用 DeepSeek 的 OpenAI 兼容接口：默认 Base URL `https://api.deepseek.com`，默认 Model `deepseek-chat`。
- 按钮位于提交信息工具栏中「历史提交信息」时钟按钮的右侧。
- Token 保存在 IDE **PasswordSafe / CredentialStore** 中，不写入项目文件，也不写入 `ridergen-gitmsg.xml`。
- 设置页内置「测试连接」按钮，可在保存前验证 Token，并区分 401/403、402、429、超时、网络不可达等错误。
- 未配置 Token 时，回退到本地模板生成，保证开箱可用。

## 版本与环境

| 项目 | 版本 |
| --- | --- |
| 目标 IDE | Rider 2026.2.3.1，build `RD-262.10968.170` |
| IntelliJ Platform | 2026.2（build 262） |
| 运行时 JDK | Rider 自带 JBR 25.0.4 |
| 构建 JDK | 使用 Rider 的 JBR 25：`<Rider安装目录>/jbr` |
| Gradle | 9.3.1（wrapper 已包含） |
| Kotlin | 2.4.0 |
| IntelliJ Platform Gradle Plugin | 2.19.0 |
| 插件兼容范围 | `since-build=262`，`until-build=262.*` |
| 插件版本 | 0.3.0 |

> 安装目录名可能仍是 `JetBrains Rider 2025.2.3`，但其中的 `product-info.json` 实际报告 `2026.2.3.1`（build 262）。本地平台路径在 [gradle.properties](gradle.properties) 的 `platformLocalPath` 中配置；在其他机器构建时改成对应的 Rider 2026.2 安装目录，或执行：
>
> ```powershell
> .\gradlew.bat buildPlugin -PplatformLocalPath="D:/path/to/Rider"
> ```

## 构建

```powershell
$env:JAVA_HOME = "D:/Procs/IDESS/JetBrains/JetBrains Rider 2025.2.3/jbr"

.\gradlew.bat clean test
.\gradlew.bat buildPlugin
```

产物：

```
build/distributions/RiderGenGitMsg-0.3.0.zip
```

## 安装

1. Rider：`Settings > Plugins`。
2. 齿轮菜单 → `Install Plugin from Disk...`。
3. 选择 `build/distributions/RiderGenGitMsg-0.3.0.zip`。
4. 重启 IDE。

## 配置 DeepSeek

打开 `Settings > Tools > RiderGenGitMsg (DeepSeek)`：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| Base URL | `https://api.deepseek.com` | 可填写根地址，插件自动拼接 `/chat/completions` 或 `/models`；也可直接填完整 endpoint |
| Model | `deepseek-chat` | DeepSeek 模型名，例如 `deepseek-chat` / `deepseek-reasoner` |
| DeepSeek API Token | 空 | 密码式输入框。保存后写入 IDE PasswordSafe，不写入 XML |
| Temperature | `0.2` | 0–2 |
| 响应语言 | 中文 | 中文（zh）/ English（en） |
| Use Conventional Commits | 开启 | `type(scope): subject` 格式 |
| Max diff characters | `24000` | 发送给模型的 diff 最大字符数 |
| 生成规则 / 额外提示词 | 空 | 多行可滚动文本框。作为 **system 提示**的一部分发送给 DeepSeek；留空保持原有默认行为，非空时优先级最高 |

### 生成规则 / 额外提示词

设置页字段名为 **「生成规则 / 额外提示词:」**，位于 `Max diff characters` 下方，是多行可滚动文本区。

- 非空内容会原样拼入 DeepSeek 请求的 **system message**（不会存入凭据库，只保存在 `ridergen-gitmsg.xml`）。
- 空值时不会出现额外规则段落，请求与 0.2.0 的默认行为一致。
- 「恢复默认规则」按钮只把内置规则填回输入框，**不会自动保存**；需要点击 **Apply** 才持久化。
- 优先级：
  1. 用户自定义规则（最高优先级，提示中明确声明会覆盖默认项）。
  2. 响应语言（中文 / English）与 Conventional Commits 开关，仅作为“用户规则未覆盖时”的默认格式。
- 因此，如果自定义规则写了“用英文”“不要 Conventional 前缀”等，模型会以自定义规则为准；开关只负责补齐未提及的方面。

内置默认规则（点击「恢复默认规则」后填入的内容）：

```text
- First line: a short imperative summary, at most 72 characters, no trailing period.
- If Conventional Commits is enabled, use a suitable type prefix (feat, fix, docs, refactor, test, build, ci, chore); omit the scope when it is unclear.
- Keep the body optional and short: 1-5 lines explaining why or impact, not a file-by-file diff dump.
- Never invent files, features, issues or breaking changes that are not visible in the diff.
- Do not output markdown fences, quotes, signatures or emojis.
```

示例提示词：

```text
使用简体中文。首行不超过 50 字，采用祈使句。
必须带 Conventional 前缀，scope 使用模块目录名。
body 用 2-4 条要点说明“为什么”，不要逐文件罗列。
不要添加表情符号或签名。
```

```text
Write in English. Start the subject with "[RiderGen]" and keep it under 60 characters.
Always include a body with exactly one line: "Impact: <one sentence>".
Never mention file names.
```

### 测试连接按钮

Token 输入框右侧的「测试连接」按钮：

1. 使用**当前输入框里的 Token**（不需要先保存）请求 `GET {Base URL}/models`。
2. 请求在后台线程执行，界面不会卡住；请求期间按钮禁用并显示「测试中…」。
3. 结果显示在按钮下方的状态标签中：
   - 成功：`Token 有效（HTTP 200），可用模型：deepseek-chat, deepseek-reasoner`
   - 401/403：`Token 无效或无权限（HTTP 401/403）…`
   - 402：`账户余额不足（HTTP 402）…`
   - 429：`请求过于频繁，已触发限流（HTTP 429）…`
   - 超时：`请求超时（30 秒）…`
   - 网络不可达：`网络不可达：无法连接 …` / `无法解析主机 …`
   - 其他非 200：显示状态码与响应摘要
   - 200 但响应体不是合法 JSON：`HTTP 200，但返回体不是合法 JSON…`

### Token 存储

- 新版本 Token 写入 `PasswordSafe.instance`（IDE 凭据存储 / 系统钥匙串），service name 为 `RiderGenGitMsg`，key 为 `DeepSeek API Token`。
- `ridergen-gitmsg.xml` 中只保留 Base URL、Model、温度、语言、Conventional Commits、最大 diff 长度、自定义生成规则等非敏感配置。
- 从 0.1.x 升级时，若 PasswordSafe 中还没有 Token，会临时回退到旧 XML 里的明文 `apiKey` 并在设置页提示迁移；点击 **Apply** 后 Token 写入 PasswordSafe，同时清空 XML 中的明文值。

## 使用

1. 在 Rider 中暂存改动（`git add`）。
2. 打开 Commit 面板。
3. 在提交信息输入框上方的工具栏中，点击时钟图标（历史提交信息）右侧的 AI 图标按钮；或按 `Ctrl+Alt+G`。
4. 插件读取 `git diff --cached`，调用 DeepSeek 生成提交信息并写入输入框。

处理规则：

- 输入框为空：直接写入生成结果。
- 输入框已有内容：生成前弹窗选择：
  - **覆盖**：替换现有内容（默认）
  - **追加**：保留现有内容，空一行后追加生成结果
  - **取消**：不发起请求
- 生成进行中再次点击不会发起并发请求，按钮会暂时禁用。
- 没有暂存改动：提示先 `git add`，不修改输入框。
- 未配置 Token：使用本地模板生成，并提示到设置页配置。
- DeepSeek 调用失败：通知中显示可读错误原因，不写入输入框，不静默覆盖已有内容。

快捷键可在 `Settings > Keymap > Other > RiderGenGitMsg` 中修改。

### 如何验证按钮位置

1. 打开 Commit 面板，展开提交信息输入框上方的工具栏。
2. 找到最右侧的时钟图标（`Show commit message history`）。
3. 该时钟右侧应紧邻一个「星芒/AI」图标，悬停提示为 `Generate commit message with DeepSeek`。
4. 点击后观察输入框被填入生成结果。

实现上，`plugin.xml` 把 Action 注册到 `Vcs.MessageActionGroup`，并声明：

```xml
<add-to-group group-id="Vcs.MessageActionGroup"
              anchor="after"
              relative-to-action="Vcs.ShowMessageHistory"/>
```

在 Rider 2026.2 的 `VcsActions.xml` 中，`Vcs.MessageActionGroup` 只包含 `Vcs.ShowMessageHistory`，因此 `after` 锚点会让本插件按钮紧跟在时钟按钮右侧。

## 测试

```powershell
.\gradlew.bat test
```

单元测试覆盖：

- 本地模板生成、`--name-status` 解析、subject 截断。
- 本地模板按自定义规则中的语言 / 前缀 / body 意图调整输出。
- DeepSeek Chat Completions 成功响应解析。
- 自定义规则确实被拼进请求体（system message），并带有最高优先级声明。
- 空规则时 system 提示与原有默认行为一致（Conventional / 纯文本两种分支）。
- 内置默认规则非空、内容完整，且作为自定义规则时能进入提示。
- `GET /models` Token 检测成功与模型列表解析。
- 401/403、402、429、网络不可达、200 非 JSON、缺少 `choices` 等错误分支。
- endpoint / models endpoint 归一化。

> 构建验证未使用真实 DeepSeek Token；上述错误分支通过本地 HTTP Server 模拟验证。

## 已知限制

- 通过 Git CLI 读取 diff，要求 `git` 在 `PATH` 中。
- 仅针对项目根目录（`project.basePath`）所在的仓库；多仓库项目请切换到对应仓库根目录。
- 使用 `java.net.http.HttpClient`，不读取 IDE 代理设置；如需代理，可通过 JVM 系统属性配置。
- 生成请求超时固定为 60 秒，Token 检测超时为 30 秒，暂未在设置页开放。
- DeepSeek 请求在后台线程执行，但 HTTP 请求本身不可取消；点击任务进度条的取消只会停止等待。
- 若生成期间关闭项目或提交对话框，结果可能不会写入输入框。
- 若 IDE 运行环境无法访问系统钥匙串，PasswordSafe 可能退回内存存储，重启后需重新输入 Token。
- 自定义规则只做**关键词级**的本地模板适配（语言、是否使用 Conventional 前缀、scope、body）；复杂格式要求只在 DeepSeek 路径生效，未配置 Token 时无法完整复刻。
- 「恢复默认规则」只填充输入框，必须点击 Apply 才会保存；关闭设置页前未 Apply 的修改会丢失。
- UI 交互（按钮填充/禁用状态）由人工验证，单元测试覆盖的是提示词拼接、默认规则内容和本地模板适配。
