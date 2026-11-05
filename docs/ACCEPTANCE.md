# 星穹旅驿 Android 客户端 v2.0.0 · 真实验收报告

> 本报告只记录本次升级中**实际执行过**的动作、**实际跑出来**的结果与**确实未完成**的部分。
> 未测量的性能指标一律标注「未测量」，不以编译成功冒充功能完成，不以模拟器/推测数据冒充真机证明。

报告生成时间：2026-11-05 · 对应提交：v2.0.0 发布提交 · 回滚点：`git tag pre-final-upgrade`

---

## 1. 交付物

| 项目 | 值 |
|---|---|
| 版本 | `2.0.0`（`versionCode=5`，`minSdk=24`，`targetSdk=35`） |
| 产物 | `dist/星穹旅驿.apk`（**不入库**，由 GitHub Release 分发） |
| 产物大小 | 6,657,364 bytes |
| 产物 SHA-256 | `866c6cc20ec8eca8ecd6d4e0814026e4f809e47fe59368553681c54699c593d6` |
| 签名证书 SHA-256 | `e1e8edc56e9700a27ab2cd4ba5f45fa0ba1bd9a88e4a261de7ef3684b3c21c05` |
| 签名方案 | v2 + v3（`apksigner verify` 通过），单签名者 |
| 发布者身份 | 与 v1.1.0 / v1.2.0 / v1.3.0 **完全一致**，可覆盖升级 |
| 一条命令构建 | `bash build.sh`（9 步，含测试、版本一致性、资源生成与产物校验） |
| 一条命令测试 | `bash tests/run-tests.sh` + `node tests/game-engine.test.js` |

签名证书指纹同时硬编码在 `core/AppIdentity.EXPECTED_SIGNING_CERT_SHA256`，客户端安装更新包时会再次比对。

---

## 2. 改造前 → 改造后对比

改造前基线：升级前本地保留的 APK `/tmp/baseline.apk`（6,574,717 bytes，SHA-256 `31f89695c174c533600d8650d6161406194db7339e89d31a838bece07a94ca11`）。
已发布的上一个版本为 v1.3.0 Release（资产 `xqly-app-v1.3.0.apk`，SHA-256 `563be59ca156848fedf58a826f02e701af1ac9e8827dd2f825d3bbb883c5ac87`）。

| 维度 | 改造前（v1.3.0） | 改造后（v2.0.0） |
|---|---|---|
| 语言 / UI 框架 | 原生 Java + View + WebView，零 Gradle | **保持不变**（见第 5 节说明） |
| JS 接口 | 3 个宽泛 `JavascriptInterface`（`Android.openPage` / `close` / dock） | **唯一** `XQLYBridge.postMessage(String)`，版本化消息 |
| 消息来源校验 | 无 | 每条消息校验版本、来源白名单、**与实际发起 WebView 同源**、类型、参数键/类型/长度、权限、幂等 id |
| WebView 默认设置 | 允许文件访问、调试开关可控 | 默认禁用文件访问 / content access / 混合内容 / 调试 / 表单与密码保存；SSL 错误一律拒绝；外链白名单 |
| 自动更新 | 查 latest release → 下载 → 系统安装 | 发布者钉扎 + Release 严格解析 + 下载白名单 + 超时/取消/断点续传 + **整包 SHA-256 + 签名证书指纹 + 版本/versionCode 非降级** 全项校验；结果写入「更新中心」；失败只删临时文件 |
| 内容完整性 | 无清单 | `assets/MANIFEST.json`（227 文件 / 10,526,099 bytes / version `1793873523`），启动时抽样哈希校验，防 ZIP 穿越、超大解压比、版本回退、截断 |
| 设计系统 | 颜色/尺寸散落在各处 | `core/DesignTokens.java` 单一真源 + `library.css` / `game.css` 令牌落地；深/浅主题 × 星穹玻璃 / 原生节能 / 增强可读三模式，共享同一套 DOM |
| 页面状态 | 成功界面 + 加载条 | `AppState` 11 种 Phase × 8 种 Page，原生 `StateOverlay` 与内置页 `showState()` 双端覆盖；每种都有文案与明确动作 |
| 用户数据 | 单一 SharedPreferences + localStorage | `xqly_user` / `xqly_cache` 分文件；schema 0→2 逐级迁移；**清缓存不删收藏/历史/进度/游戏存档** |
| 搜索 | 档案页关键字过滤 | `SearchIndex` 本地全文检索（CJK 单字 + bigram、标题/标签加权、`<mark>` 高亮、上下文预览、筛选、最近搜索）；随包真实索引 13 条，可持久化 |
| 游戏 | 单文件 `game.html`，localStorage，`Math.random()` | 规则引擎 `core/GameRules.java` + `assets/game-engine.js` 双实现，确定性 seed、撤销次数、hold/下一块预览、每日挑战、成就、色盲形状、暂停/恢复、**存档走宿主用户数据区** |
| 功能开关 | 无 | `FeatureFlags` + `FeatureGate` + `assets/config/flags.json`，Kill Switch 默认安全值（滚动收拢、音效默认关） |
| 自动化测试 | **0** | JVM 104 用例 + Node 19 用例，共 **123 个**，全部通过；含随包资源真实验证 |
| 构建链 | 7 步，无测试、无版本校验 | 9 步：版本一致性 → JVM 测试 → Node 测试 → 索引/清单生成与校验 → 编译 → 打包 → APK 内清单比对 → 签名 → 摘要输出 |
| 内置 assets | 220 文件 | 227 文件（`MANIFEST.json`、`bridge-shim.js`、`library.html/css`、`game.css`、`flags.json`、`search-index.json`、`game-engine.js` 等） |
| Java 源码 | 10 文件 | 25 文件 / 4,941 行（新增 14 个纯逻辑 `core` 类 + 5 个 Android 层类） |

---

## 3. 实际测试结果（可复现）

```
bash tests/run-tests.sh          → 104 passed, 0 failed, 104 total
node tests/game-engine.test.js   →  19 passed, 0 failed,  19 total
bash build.sh                    → 9 步全部通过，产物 SHA-256 / 证书指纹见第 1 节
```

JVM 测试覆盖：`MiniJson` 严格解析与确定性序列化、`VersionPolicy` 版本与降级、`UrlPolicy` 白名单与路径穿越、`BridgeProtocol` 版本化消息与伪造来源、`UpdateVerdict` 可信更新链、`ContentManifest` 内容包攻击面、`DesignTokens` 令牌与 WCAG 对比度、`FeatureFlags` Kill Switch、`GameRules` 确定性引擎、`SearchIndex` 检索、`UserData` 迁移与隔离、`AppState` 全状态、**随包真实索引与清单哈希**。

Node 测试覆盖：确定性 PRNG、四方向合并与计分、无效移动、撤销次数、hold、结束判定、等级、存档往返与非法存档裁剪、每日种子、色盲形状、成就解锁。

APK 静态校验（已执行）：

```
aapt dump badging → versionCode='5' versionName='2.0.0' minSdk 24 targetSdk 35
apksigner verify  → v2 true, v3 true, 1 signer
unzip -l          → 245 个文件；MANIFEST.json / search-index.json / flags.json /
                    bridge-shim.js / library.html / library.css / game.css /
                    game-engine.js 均在包内
cmp               → APK 内 assets/MANIFEST.json 与源码树字节一致
```

**未执行**（原因见第 5 节）：真机安装/覆盖升级实测、Compose UI 测试、Macrobenchmark、Baseline Profile、冷启动/帧率/内存数字、TalkBack 与 200% 字体真机走查、弱网/断网/慢存储/低电量设备矩阵。

---

## 4. 逐项对照需求

### 4.1 已关闭的发布与安全风险

- **自动更新**：`Updater` 重写为可信更新链；`UpdateVerdict.evaluate` 输出 publisher / asset_name / asset_host / size / sha256 / signature / version / apk_version 逐项 `Check`；任一失败即拒装并删除临时文件，当前版本完好。`UpdateCenter` 展示当前版本、通道、发布者、签名钉扎、上一可用版本、校验项与最近记录（URL 与长文本脱敏后摘要保存）。系统限制无法直接安装时一次性引导，不反复弹窗。
- **WebView**：`configureWeb` 默认加固；`UrlPolicy.classify` 严格协议/主机/路径白名单（拒绝 `javascript:` / `data:` / `content:` / 明文官方站 / userinfo / 非常规端口 / `..`）；`shouldInterceptRequest` 拦截 BLOCKED 资源并返回 403；`WebBridge` 唯一入口；`BridgeProtocol` 拒绝危险键（token/file/url/cmd/install/exec/cookie/script）；加载超时、错误、离线缓存状态均有 UI；`xqlypause` / `xqlyresume` 暂停页面定时器与动画。
- **数据与内容包**：`.gitignore` 排除 `*.jks` / `build/` / `dist/` / `.env`；令牌只从环境变量读取；日志不输出令牌、完整 URL 查询、账号或全文内容；`ContentManifest` 校验清单 → 抽样文件 SHA-256 → 校验失败保留当前可用版本；`PrefsStore` 用户/缓存隔离。

### 4.2 设计系统与适配

- `DesignTokens`：语义色、亮/暗/增强对比三套映射、4dp 栅格、8/12/16/24/32dp 间距、分级圆角、按压/悬停/选中/禁用/加载/错误态、四级动效时长、玻璃参数、`meetsAA()` WCAG 对比度计算。
- 三模式共享导航与业务状态：`applyWebTheme()` 把 `data-theme` / `data-mode` 写到每个内置页 `<html>`，CSS 只覆盖令牌，不复制页面。
- 玻璃只用于 Dock / 悬浮工具 / 弹层；正文用稳定实底色；统一左上光源、同心圆角、单层阴影。
- 自适应：`computeScale()` 判定平板 / 横屏 / 展开折叠屏，横屏与大屏切 `GlassDock.setVerticalRail(true)`；正文最大宽度 `--maxw: 720px`。
- 无障碍（**代码层面已实现，未做真机走查**）：`focus-visible` 焦点环、`aria-label` / `role` / `aria-live`、≥48dp 触控目标、`sp`/`rem` 字号、`prefers-reduced-motion`、色盲形状、`data-mode="contrast"` 扩大焦点与目标、键盘方向键/WASD/H/U/N/Esc 映射。

### 4.3 功能

- 阅读与资料：在线/离线统一模型、搜索/高亮/预览/筛选/最近搜索、收藏/标签/收藏夹/历史/书签/阅读进度、字号/行距/主题/正文宽度设置、schema 迁移。
- 离线：227 文件内置镜像 + 13 条真实索引，断网可进入、阅读、搜索、收藏、玩游戏；内容版本在资料页展示（来自真实清单校验结果，非占位）。
- 更新中心：见 4.1。
- 方块合成 2.0：开始/继续/重开/每日挑战/暂停恢复、自动存档到宿主用户区、输入缓冲与滑动/键盘/鼠标、下一块与 hold 预览、分数/最高/等级/连击、明确 3 次撤销、成就、本地确定性每日种子、色盲形状、低性能模式（eco/contrast 关闭动画）、音效与震动默认关闭。**无广告、无付费、无隐性追踪、无联网**。
- 资料检索预备层：精确检索 + 来源定位，**界面与代码均不称其为 AI**；无结果明确告知；不上传任何用户内容。

### 4.4 架构与代码质量

- `core/` 14 个纯 Java 类，**零 Android 依赖**，可在标准 JVM 编译测试；Android 层只做装配与生命周期。
- 单向数据流：`WebBridge` → `BridgeProtocol` → `MainActivity` → `reply()`；UI 状态由 `AppState` / `StateOverlay` 统一表达。
- 幂等：`BridgeProtocol` 的 `seenIds` 去重有界（512 条后清空）；`Updater` 下载取消与重复点击防护。
- 保留了已验证稳定的原生 Java / aapt2 / javac / d8 / apksigner 构建链，没有删除旧代码盲重写。

---

## 5. 未完成项与真实原因（不伪造、不冒充）

| 未完成项 | 原因 | 人工补齐路径 |
|---|---|---|
| **Kotlin + Jetpack Compose + Material 3 全量重写** | 沙箱无 `kotlinc`、无 AGP / Compose / Material3 依赖缓存；`maven.google.com` 探测 8 秒超时不可达（`dl.google.com` 与 `repo1.maven.org` 可达但不足以支撑）；且需求明确禁止「一次性删除全部旧代码盲重写」与「为 Compose 纯度重写稳定且正确的原生代码」。 | 见 [MIGRATION.md](MIGRATION.md)：给出包级映射与分阶段迁移顺序，`core/` 可原样复用。 |
| 真机 / 平板 / 折叠屏 / 弱网 / 低内存设备矩阵实测 | 环境无 `emulator`、无 `adb`，无法安装 APK 或截图。 | 在装有 Android 7/8 低内存机、主流手机、平板、折叠屏上执行安装、覆盖升级、旋转、断网、弱网、低电量走查。 |
| 冷启动 P50/P90、帧时间 P90、WebView 首绘、30 分钟内存、Macrobenchmark、Baseline Profile | 无可运行设备，无法采集；需求明确禁止「未测试性能数字」。 | 在真机上用 Macrobenchmark 采同一场景改造前后数据，再生成并安装 Baseline Profile。本报告**不给出任何性能数字**。 |
| Compose UI 测试 / TalkBack / 200% 字体 / 键盘 Tab 顺序真机走查 | 依赖上面两项。 | 接入 Compose 后补 UI 测试；在当前版本可用真机先验证已有 aria / focus / 触控目标实现。 |
| 内容包下载中心 UI（暂停/限速/仅 Wi-Fi/双槽一键回退） | 校验逻辑（`ContentManifest`）与状态机已完成，但当前**没有真实内容包服务器**；需求禁止依赖虚构服务器，因此未做「假下载」界面。 | 有了真实内容包来源后，按 `ContentManifest.verify → 临时下载 → 校验 → 原子切换` 接 UI，双槽保存新旧版本。 |
| 后台下载使用前台服务 | 同上，当前更新下载在应用前台进行，未伪造后台能力。 | 接入系统推荐的前台服务 / WorkManager，遵守后台限制。 |
| 图片按显示尺寸解码与节省流量 | WebView 内未做专门图片管线改造。 | 视内容规模决定是否引入；当前离线镜像为本地资源，无流量成本。 |

> 说明：需求中「可选本地智能功能」明确要求「没有真实模型与授权时界面称为资料检索，不冒充 AI」——本项目**没有**引入任何模型或云端接口，符合该约束。

---

## 6. 安全与合规自检

- 未提交 keystore、Token、密码、`.env`；`git status` 中无 `.jks` / `.apk`；`build/xqly.jks` 与 `/root/.xqly-keys/xqly.jks` 均在 `.gitignore` 覆盖或仓库外。
- 发布者身份未改变：v2.0.0 与 v1.3.0 使用同一证书，可覆盖升级。
- 未静默删除用户数据：缓存清理只作用于 `xqly_cache`；迁移失败不清空。
- 未用 try-catch 掩盖根因：桥层捕获仅为不向页面回抛异常并计数，业务逻辑异常有明确状态与重试入口。
- 未加入广告、挖矿、赌博、诱导充值或隐性追踪。
- 未使用模拟器截图冒充真机证明；`docs/*.png` 是**历史阶段**的设计预览图，本报告已明确标注其非本次验收证据。

---

## 7. 回滚计划

1. **代码**：`git checkout pre-final-upgrade` 回到升级前提交；或 `git revert <v2.0.0 提交>`。
2. **已发布 APK**：v1.3.0 Release 保留不动，用户可手动下载回退；v2.0.0 的自动更新链会拒绝降级安装，因此回退需用户手动安装旧版（符合「拒绝版本降级」的安全要求）。
3. **客户端内**：任何校验失败都只删除临时下载文件，当前可用版本完整保留；`UpdateCenter` 显示上一可用版本与回退说明。
4. **内容**：`verifyBundledContent()` 抽样校验不通过时不写入 `pack.version`，继续使用内置基线，不切换、不覆盖。

---

## 8. 发布后校验（已执行）

发布流程见 [RELEASE.md](RELEASE.md)。以下均为发布后实际回读的结果：

| 校验项 | 结果 |
|---|---|
| 源码提交 | `a371377` 推送至 `main`，远端 HEAD 与本地一致；回滚点 `pre-final-upgrade` 保留 |
| Release tag | `v2.0.0`，`draft=false`、`prerelease=false` |
| `html_url` | `https://github.com/ChaosSurvivalProject/xqly-app/releases/tag/v2.0.0` — 钉扎发布者通过 |
| 资产名 | `xqly-app-v2.0.0.apk` — `validAssetName` 与 tag 一致 |
| 资产大小 | 6,657,364 bytes，与本地产物一致 |
| 资产 digest | `sha256:866c6cc20ec8eca8ecd6d4e0814026e4f809e47fe59368553681c54699c593d6` — 与本地 SHA-256 字节级一致 |
| 下载主机 | `github.com`，在 `DOWNLOAD_HOSTS` 白名单内 |
| `/releases/latest` | 指向 `v2.0.0`（自动更新链可发现） |
| **端到端解析** | 用客户端真实的 `UpdateVerdict.parseRelease` 直接解析线上 `/releases/latest` 原始 JSON：`parseError = OK`、`assetValid = true`、`hostOK = true`、`digest` / `size` 读取正确 |

最后一项不是模拟数据：校验程序在标准 JVM 上加载了本仓库编译出的 `core` 类，输入是 GitHub API 实时返回的 JSON。这意味着 v2.0.0 客户端启动后查询更新时，能正确解析并进入后续的下载与签名/摘要校验流程。
