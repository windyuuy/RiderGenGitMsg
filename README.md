# AIGenCommitMsg

JetBrains Rider 插件：根据 **已暂存改动**（`git diff --cached`）调用 **DeepSeek** 生成 Git 提交信息，并写入 Commit 面板的提交信息输入框。

- 插件显示名：**AIGenCommitMsg**（不含 IDE / 平台品牌词，已避开 Marketplace 上已有的中文同名插件）
- 插件 ID：`dev.gitmsg.ai.commithelper`
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
| 插件兼容范围 | `since-build=262`（按平台建议不设置 `until-build`，向前兼容） |
| 插件版本 | 0.6.0 |

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
build/distributions/AIGenCommitMsg-0.6.0.zip
```

## 安装

1. Rider：`Settings > Plugins`。
2. 齿轮菜单 → `Install Plugin from Disk...`。
3. 选择 `build/distributions/AIGenCommitMsg-0.6.0.zip`。
4. 重启 IDE。

## Marketplace 上传注意事项

- **命名规则**：`plugin.xml` 的 `<name>` 不得包含 `Rider`、`IntelliJ`、`JetBrains`、`IDEA` 等 IDE / 平台品牌词。当前显示名 `AIGenCommitMsg` 不含任何品牌词；`<id>` 使用中性的反向域名 `dev.gitmsg.ai.commithelper`。
- **显示名变更（0.5.0）**：Marketplace 上已存在名为「AI 提交消息生成器」的插件，导致上一版显示名被拒。0.5.0 起显示名改为 `AIGenCommitMsg`，`<id>` 保持不变，因此仍作为同一插件的版本更新。若仍提示名称冲突，可依次尝试 `AIGenCommitMsg DeepSeek`、`AIGenCommitMsg AI Commit Writer`、`AIGenCommitMsg for DeepSeek`。
- **`id` 变更影响**：Marketplace 以插件 ID 作为更新身份。本次从 `dev.ridergen.gitmsg` 改为 `dev.gitmsg.ai.commithelper` 后，Marketplace 会把它视为一个新插件，旧 ID 的版本无法作为升级包。
- **必填字段**：`description`、`vendor`（名称 + URL）、`idea-version`（`since-build=262`）、`change-notes` 均已在 [plugin.xml](src/main/resources/META-INF/plugin.xml) 与 [build.gradle.kts](build.gradle.kts) 中填写。平台校验器建议 build 243+ 不设置 `until-build`，因此这里只声明 since build。
- **vendor URL**：取自仓库真实 remote `origin`，最终值为 `https://github.com/windyuuy/RiderGenGitMsg`（`plugin.xml`、`build.gradle.kts`、本 README 已统一）。若之后仓库迁移或改名，请同步修改这三处。
- **上传前仍需手动准备**：
  - 开源许可证：仓库已包含 `LICENSE`（Apache License 2.0），上传时在 Marketplace 表单选择 Apache-2.0。
  - 截图（建议至少准备 1–3 张：提交工具栏按钮、设置页、生成结果）与支持的 issue 链接。
  - 在 Marketplace 表单中确认支持范围 `since-build=262`（未设置 `until-build`）与实际上架的 IDE 版本一致。
  - 若需要，填写支持邮箱 / 反馈地址。

### 图标

| 用途 | 文件 | 规格 |
| --- | --- | --- |
| IDE 内浅色主题 | [pluginIcon.svg](src/main/resources/META-INF/pluginIcon.svg) | 40×40 SVG，透明背景，`#2563EB` / `#60A5FA` |
| IDE 内深色主题 | [pluginIcon_dark.svg](src/main/resources/META-INF/pluginIcon_dark.svg) | 40×40 SVG，透明背景，`#8AB4F8` / `#C7DCFE` |
| Marketplace 40px | [icon-40.png](assets/marketplace/icon-40.png) | 40×40 PNG，RGBA，透明背景 |
| Marketplace 80px | [icon-80.png](assets/marketplace/icon-80.png) | 80×80 PNG，RGBA，透明背景 |

- `plugin.xml` 中声明 `<icon>META-INF/pluginIcon.svg</icon>`；IntelliJ 平台本身也会自动从 `META-INF/pluginIcon.svg` / `META-INF/pluginIcon_dark.svg` 读取插件图标。
- PNG 由 [tools/generate_icons.py](tools/generate_icons.py) 使用 Pillow 生成（几何形状与 `pluginIcon.svg` 完全一致，8× 超采样后降采样抗锯齿），脚本会自动读回文件校验尺寸与透明度：

  ```powershell
  python tools/generate_icons.py
  # OK assets\marketplace\icon-40.png: 40x40 RGBA alpha=0..255 content_bbox=(2, 3, 38, 35)
  # OK assets\marketplace\icon-80.png: 80x80 RGBA alpha=0..255 content_bbox=(5, 5, 74, 65)
  ```

### Marketplace 英文介绍（可直接复制）

```text
Generate a Git commit message from your staged changes in one click.

AIGenCommitMsg adds a button to the commit message toolbar, right next to the message history button, and fills the commit message field using the DeepSeek Chat Completions API. Configure the API token, model, temperature, output language, Conventional Commits and custom generation rules under Settings > Tools > AIGenCommitMsg (DeepSeek).

Highlights:
- One-click generation from staged changes (git diff --cached), with overwrite/append/cancel handling for a non-empty message field.
- DeepSeek-only integration with a configurable base URL (default https://api.deepseek.com) and model (default deepseek-chat).
- API token stored in the IDE PasswordSafe, never in project files.
- Built-in Test Connection button with clear errors for invalid token, insufficient balance, rate limiting, timeouts and network failures.
- Searchable commit-message language picker with 213 languages and region variants, matched by country code, English name or native name.
- Custom generation rules appended to the system prompt with the highest priority.
- Offline template fallback when no token is configured, so the action always works.

Supported IDE: 2026.2 and later (build 262+).
```

### 改名与持久化兼容

为避免已有用户配置丢失，以下内部 key **保持不变**：

- 设置状态组件：`@State(name = "RiderGenGitMsgSettings")`，存储文件 `ridergen-gitmsg.xml`。
- 凭据库 service name：`RiderGenGitMsg`，key 为 `DeepSeek API Token`。
- 通知组 id `AICommitMessageGenerator`、Action id `AICommitMessageGenerator.GenerateCommitMessage`：仅内部标识，保留以免已有快捷键映射失效。
- Kotlin 包名 `dev.ridergen.gitmsg` 与类名（如 `RiderGenSettings`、`RiderGenConfigurable`）保留，仅属内部实现，不影响 Marketplace 显示。

## 配置 DeepSeek

打开 `Settings > Tools > AIGenCommitMsg (DeepSeek)`：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| Base URL | `https://api.deepseek.com` | 可填写根地址，插件自动拼接 `/chat/completions` 或 `/models`；也可直接填完整 endpoint |
| Model | `deepseek-chat` | DeepSeek 模型名，例如 `deepseek-chat` / `deepseek-reasoner` |
| DeepSeek API Token | 空 | 密码式输入框。保存后写入 IDE PasswordSafe，不写入 XML |
| Temperature | `0.2` | 0–2 |
| 提交信息语言 | `US · English` | 可搜索下拉，内置 **213 条**语言 / 地区变体，覆盖 162 个国家/地区。详见下文「提交信息语言」 |
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
  2. 提交信息语言（可搜索下拉）与 Conventional Commits 开关，仅作为“用户规则未覆盖时”的默认格式。
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
Write in English. Start the subject with "[AICG]" and keep it under 60 characters.
Always include a body with exactly one line: "Impact: <one sentence>".
Never mention file names.
```

### 提交信息语言（0.6.0+）

设置页 `提交信息语言 / Commit message language:` 是可搜索下拉，替代旧版的「中文 / English」二元选项。

- **条目格式**：`国家代码 · 语言英文名 · 语言本土名`，例如 `CN · Chinese (Simplified) · 简体中文`、`SA · Arabic · العربية`、`JP · Japanese · 日本語`。三个部分始终完整显示。
- **数据规模**：内置 **213 条**，覆盖 **162 个 ISO 3166-1 alpha-2 国家/地区**，地区变体分开列出（CN / TW / HK 中文，US / GB / AU / CA / IE / NZ 等 English，BR / PT 葡萄牙语，ES / MX / AR 等西班牙语）。
- **搜索**：点击下拉框后顶部有搜索输入框，按 **国家代码**（如 `cn`）、**英文名**（如 `chinese`、`simplified`）或 **本土名**（如 `简体`、`العربية`）任意子串匹配，大小写不敏感，实时刷新；无匹配时列表显示 `No matching languages / 无匹配语言`。
- **键盘操作**：搜索框内 `↓` 进入列表、`Enter` 选中第一条、`Esc` 关闭；列表内 `Enter` 选中、`Esc` 关闭。
- **随机顺序**：控件下方固定提示：「语言列表顺序为随机排列，与语言热度、推荐程度或任何其它因素无关。」实现上每次打开设置对话框（`reset()`）都会用 `Random` 重新打乱展示顺序，**不会**按字母序排列；当前已选语言通过独立字段回显，不受打乱影响。
- **写入提示词**：选中的语言会写入 DeepSeek 的 system 提示，例如 `Write the commit message in Japanese (日本語)`；English 等英文名与本土名相同的条目只写一次。自定义规则仍标注为 `highest priority`，并在语言默认行注明 `unless the user rules above specify a different language`。
- **持久化与迁移**：新字段为 `commitLanguage`，格式 `CC|EnglishName`（如 `US|English`），保存在 `ridergen-gitmsg.xml`。旧字段 `responseLanguage`（`zh` / `en`）在 `loadState` 时自动迁移：`zh` → `CN · Chinese (Simplified)`，`en` → `US · English`；未设置时默认 `US · English`。点击 Apply 后旧字段清空，新字段接管。
- **本地模板回退**：未配置 Token 时，中文（CN/TW/HK 等）使用中文模板，English 各变体使用英文模板；其他语言暂用英文模板，并在通知中明确说明「当前所选语言「…」暂无本地模板，已使用英文模板生成」，不会静默输出错误语言。自定义规则若显式要求中文/英文，本地模板会按规则覆盖。

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
- 未配置 Token：使用本地模板生成，并提示到设置页配置；若所选语言没有本地模板（非中文/英文），会使用英文模板并在通知中说明。
- DeepSeek 调用失败：通知中显示可读错误原因，不写入输入框，不静默覆盖已有内容。

快捷键可在 `Settings > Keymap > Other > AIGenCommitMsg` 中修改。

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
- 语言列表完整性（数量 ≥ 100、`code+英文名` 无重复、字段非空、必需国家与地区变体齐全）。
- 语言搜索：国家代码 / 英文名 / 本土名任意子串、大小写不敏感、无结果。
- 旧 `zh` / `en` 迁移到 `commitLanguage`、存储值往返解析。
- 选中语言（含本土名）写入 system 提示，自定义规则仍为最高优先级。
- 随机打乱后条目不丢失、已选语言仍能正确回显。
- 中文 / 英文 / 无模板语言的本地回退行为。
- DeepSeek Chat Completions 成功响应解析。
- 自定义规则确实被拼进请求体（system message），并带有最高优先级声明。
- 空规则时 system 提示使用默认语言（US · English）与 Conventional / 纯文本分支。
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
- 本地模板只为中文、英文准备了固定文案；其他 190+ 种语言在未配置 Token 时使用英文模板，并通过通知明确提示。
- 阿拉伯语、希伯来语等 RTL 本土名由 Swing 按 Unicode 双向算法直接渲染，不做强制变形；极端字体缺失时可能出现字形替换。
- 语言列表使用 `Random()` 每次打开设置页重新打乱；不提供手动排序或收藏，定位依赖搜索框。
- 初始化时迁移只读取旧 `responseLanguage`；若用户从未保存过配置，则默认 `US · English`，不跟随 IDE 界面语言。
- 「恢复默认规则」只填充输入框，必须点击 Apply 才会保存；关闭设置页前未 Apply 的修改会丢失。
- UI 交互（按钮填充/禁用状态、下拉搜索）由人工验证，单元测试覆盖的是语言数据、搜索、迁移、提示词拼接与本地回退。
