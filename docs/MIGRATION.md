# Kotlin + Jetpack Compose + Material 3 迁移映射

> **当前状态：未实施全量重写。** 本文档是给后续有完整 Android 工具链的环境准备的迁移蓝图，
> 不是「已完成」声明。原因与验收结论见 [ACCEPTANCE.md](ACCEPTANCE.md) 第 5 节。

## 1. 为什么 v2.0.0 没有做这次重写

| 约束 | 实测情况 |
|---|---|
| Kotlin 编译器 | `kotlinc` / `kotlin` 均不存在 |
| Gradle / AGP / Compose / Material3 | 无依赖缓存；`~/.gradle/init.gradle` 还存在 `mavelCentral()` 拼写错误与未加引号 URL |
| 依赖仓库 | `maven.google.com` 8 秒超时不可达；`dl.google.com`、`repo1.maven.org` 可达但不足以支撑完整 Compose 工具链 |
| 设备验证 | 无 `emulator`、无 `adb`，无法做 Compose UI 测试、截图或性能采集 |
| 需求本身的禁止项 | 明确禁止「一次性删除全部旧代码进行盲重写」「为 Compose 纯度重写稳定且正确的原生代码」「把编译成功当功能完成」「未测试性能数字」 |

在这些条件下强行产出一份「看起来是 Compose」的代码，既无法编译，也无法安装、无法测试、无法回滚，与需求「直接交付可编译、可安装、可测试、可回滚的最终成果」直接冲突。因此本次选择：**保留已验证可编译可签名的原生架构，把能真实落地的安全、设计系统、状态机、逻辑、搜索、游戏、更新链与测试先做实**，并把迁移路径完整留下。

## 2. 现有包 → 目标模块映射

| 现有文件 / 包 | 目标模块（建议） | 处理方式 |
|---|---|---|
| `core/MiniJson.java` | `domain:json` / `platform:security` | **原样复用**，纯 Java 无 Android 依赖，Kotlin 可直接调用；后续可换成 `kotlinx.serialization` 但要保留严格解析语义（拒绝尾随内容 / NaN / 深嵌套） |
| `core/AppIdentity.java` | `platform:security` | 原样复用，发布者 / 主机 / 桥版本 / 证书钉扎唯一真源 |
| `core/VersionPolicy.java` | `domain:update` | 原样复用 |
| `core/UrlPolicy.java` | `platform:security` | 原样复用，Compose 的 WebView 包装层继续调用同一白名单 |
| `core/BridgeProtocol.java` | `platform:security` | 原样复用；`WebBridge` 换成 Compose `AndroidView` 内的 bridge 实例，协议不变 |
| `core/ContentManifest.java` | `data:contentpack` | 原样复用，作为 Repository 的校验层 |
| `core/UpdateVerdict.java` | `domain:update` | 原样复用，`ViewModel` 把 `Verdict` 映射成 `UpdateUiState` |
| `core/DesignTokens.java` | `designsystem` | **语义令牌迁移到 Compose**：`ColorKey` → `ColorScheme` / 自定义 `XqlyColors`，间距与圆角 → `Dimensions` / `Shapes`，动效 → `MotionScheme`；保留 `meetsAA()` 作为设计评审断言 |
| `core/FeatureFlags.java` | `domain:config` | 原样复用，Repository 注入 |
| `core/GameRules.java` | `domain:game` | 原样复用，纯 JVM；Compose 只渲染 `State` |
| `core/SearchIndex.java` | `domain:search` | 原样复用；大索引迁到 Room FTS4/FTS5 或持久化文件，仍在 IO 线程 |
| `core/SigningCert.java` | `platform:security` | 原样复用 |
| `core/UserPrefs.java` / `UserData.java` | `data:user` | 迁到 DataStore / Room，保留 schema 0→2 迁移语义与 `u.` / `cache.` 命名空间隔离，并补真实版本迁移测试 |
| `core/AppState.java` | `designsystem:state` | 转为密封接口 `UiState`，`StateOverlay` → 通用 `@Composable StateHost` |
| `MainActivity.java` | `app` + `shell` | 拆成 `XqlyApplication`、`MainActivity`（ComponentActivity）、`XqlyNavHost`、`XqlyAppState`；WebView 用 `AndroidView` 包装，生命周期用 `DisposableEffect` |
| `WebBridge.java` | `feature:web` | 保留唯一 `postMessage`，`Host` 由 ViewModel 实现 |
| `Updater.java` | `feature:update` + `platform:download` | 拆为 `UpdateRepository`（校验/下载）+ `UpdateViewModel`（状态）+ `UpdateCenterScreen`（Compose UI）；校验逻辑不动 |
| `UpdateCenter.java` | `feature:update` | 由 AlertDialog 改为 Compose Screen，复用 `UpdateVerdict.Verdict` |
| `PrefsStore.java` / `FeatureGate.java` | `data` / `domain:config` | 由 DI（Hilt/Koin）提供可替换实现 |
| `StateOverlay.java` | `designsystem` | 改为 `StateHost` Composable |
| `GlassDrawable.java` / `GlassDock.java` | `designsystem:glass` | **保留原生 Canvas 实现**，通过 `AndroidView` 嵌入；或用 Compose `Canvas` + `Modifier.graphicsLayer` 重写并做视觉回归。不建议为了纯度丢弃已调好的折射/邻近放大 |
| `XFileProvider.java` | `app` | 保留，Manifest 声明不变 |
| `assets/game-engine.js` | `feature:game` | 若把游戏也 Compose 化，规则改调 `GameRules`；当前 DOM 版可继续用 `AndroidView` |
| `assets/library.css` / `game.css` | `designsystem` | 令牌值应与 Compose `XqlyColors` / `Shapes` 保持同源（建议生成脚本同步常量） |
| `tests/CoreTestSuite.java` | `testing` | 迁移为 JUnit5 + Kotest/Truth；123 个用例语义全部保留 |
| `tests/game-engine.test.js` | `testing` | 保留，或随游戏 Compose 化改为 `GameRules` 的 Kotlin 测试 |
| `build.sh` | `build-logic` | 换成 Gradle Version Catalog + convention plugins；保留 9 步中的「测试 → 资源生成 → 版本一致性 → 产物摘要」门禁 |

## 3. 分阶段迁移顺序（建议）

1. **建基线**：在有 AGP 的环境先建立空 Compose 模块，确认 `./gradlew :app:assembleDebug` 与真机安装可用；记录冷启动/帧率基线。
2. **复用 core**：把 `core/` 14 个类作为独立 `jvm` 模块接入，跑现有 104 个 JVM 用例（改为 JUnit）确保零回归。
3. **接入设计系统**：把 `DesignTokens` 映射到 `MaterialTheme` + 自定义令牌，实现三模式切换与 WCAG 断言预览。
4. **逐屏替换**：按 首页 → 资料/搜索 → 收藏 → 更新中心 → 设置 → 游戏 的顺序，每屏替换都保留 `AndroidView` 回退开关（`FeatureFlags` 增加 `compose_<screen>`）。
5. **WebView 包装**：最后替换 WebView 层，因为安全白名单与桥协议最敏感；保持 `UrlPolicy` / `BridgeProtocol` 不变。
6. **原生玻璃**：Dock 与 GlassDrawable 用 `AndroidView` 长期共存，视觉稳定后再考虑重写。
7. **补测试与性能**：Compose UI 测试、Macrobenchmark、Baseline Profile、真机设备矩阵；每项都在真实数据出来后才填进验收报告。
8. **移除门禁**：当所有 `compose_*` 开关稳定为 true 且测试全绿，才删除旧 View 代码，并保留 git 迁移记录。

## 4. 人工补齐清单

- [ ] 安装 JDK 17 + Android Studio / AGP 8.x + Kotlin 2.x + Compose BOM，修复 `~/.gradle/init.gradle` 拼写错误。
- [ ] 确保 `maven.google.com` 可达（或配置可信镜像），锁定依赖版本到 Version Catalog。
- [ ] 准备真机矩阵：Android 7/8 低内存机、主流手机、平板、折叠屏、弱网/断网/慢存储/低电量。
- [ ] 采改造前后同场景性能数据，生成并安装 Baseline Profile。
- [ ] 完成 TalkBack、键盘 Tab/方向键、200% 字体、外接鼠标/触控笔走查。
- [ ] 按第 3 节逐阶段迁移，每阶段跑完整测试与回归。
- [ ] 若引入内容包服务器：补双槽、前台服务下载、限速/仅 Wi-Fi 与一键回退 UI。
