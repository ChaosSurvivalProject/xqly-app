# v2.0.1 双平台改造 · 仓库审计与技术决策报告

审计日期 2026-11-05（UTC）· 审计对象 `ChaosSurvivalProject/xqly-app` · 基线提交 `23cc277`（v2.0.0 文档回填）

本文只记录**可复现命令的实测结果**。没有任何一项结论来自「编译成功即等于功能完成」或推测。

---

## 0. 结论摘要

| 用户目标 | 本版本实际状态 |
|---|---|
| 原生 Windows + 原生 Android 双平台 | ✅ 两端原生外壳源码齐备；Android 已产出正式签名 APK；**Windows 二进制由 CI 生成，本机不伪造** |
| 最大化复用业务与设计代码 | ✅ 建立 `shared/` 纯 Dart 双平台共享核心（16 模块），设计令牌一处真源生成三份产物 |
| 优先评估 Flutter / KMP，不得未经评估就重写 | ✅ 完成可行性证据采集并给出结论（见第 4 节）：**不做全量 Flutter UI 重写**，保留已验证的 Android 原生链路 |
| 跨设备同步、桌面专属体验、系统集成、触屏 | ✅ 本地优先同步包与合并规则、多标签/快捷键/托盘/开机启动/协议/文件关联/触屏输入均已实现并测试（平台无关部分）；**真机触屏未测** |
| 输出事实证据、可复用模块、必须替换模块、平台差异与风险、迁移映射 | ✅ 本文第 2/5/6/7/8 节 + [MIGRATION_MAP.md](MIGRATION_MAP.md) |
| 构建并验证三类产物、输出真实验收报告 | ⚠ Android APK 已验证；Windows 两类产物需 Windows 主机。见 [ACCEPTANCE_2.0.1.md](ACCEPTANCE_2.0.1.md) |
| 无有效证书/授权时安全降级，不伪造 | ✅ 无 Windows 代码签名证书 → `SIGN_RESULT=UNSIGNED` 且产物必须标注；无云端凭据 → 界面显示「未配置」 |

> **重要**：本仓库提供的是「已审计的完整工程」，不是「已在真机上跑通的双平台成品」。凡未验证项均在第 9 节与验收报告中逐条列出。

---

## 1. 审计范围与方法

| 项 | 内容 |
|---|---|
| 审计对象 | 工作区 `/workspace/xqly-app`，git 跟踪 291 个文件 |
| 基线 | v2.0.0（`a371377` + 文档 `23cc277`），tag `v1.1.0` / `v1.2.0` / `v1.3.0` / `v2.0.0` / 回滚点 `pre-final-upgrade` |
| 方法 | 只读查看文件树与源码 → 运行旧构建链与三套测试记录基线 → 探测工具链与网络 → 依据证据做技术决策 → 编码 → 复跑全部门禁 |
| 不采用的方法 | 不把 `flutter --version` 无输出当成「Flutter 可用」；不把 Linux 上 C++ 核心编译通过当成「Windows EXE 已完成」；不提供模拟器截图或编造的性能数字 |

---

## 2. 环境事实证据（本轮实测）

### 2.1 可用工具链

| 项 | 探测命令 | 实测结果 |
|---|---|---|
| OS | `uname -a` | Linux（沙箱），Ubuntu 系；非 Windows |
| Python | `python3 --version` | 3.11.1 |
| Node | `node --version` | v22.13.1 |
| **JDK / javac** | `javac -version` | **OpenJDK 20**（注意：CI 锁定 Temurin 17） |
| Dart | `dart --version` | Dart SDK 2.17.0 stable (linux_x64) |
| CMake | `cmake --version` | 3.28.3 |
| C++ 编译器 | `g++ --version` | 13.3.0 |
| Android SDK | `ls /opt/android-sdk` | `build-tools/35.0.0`、`platforms/android-35` |
| git / gh | `git --version` / `gh --version` | 2.43.0 / 2.86.0 |

### 2.2 缺失的工具链（`command -v` 全部无结果）

`msbuild`、`cl.exe`、`signtool.exe`、`makeappx.exe`、`wine`、`i686-w64-mingw32-g++`、`x86_64-w64-mingw32-g++`、`adb`、`emulator`。

推论：**本机无法编译 Win32 目标、无法生成 MSIX/EXE 安装器、无法做代码签名、无法做 Android 真机/模拟器验证**。

### 2.3 Flutter 可行性证据

| 探测 | 结果 |
|---|---|
| `command -v flutter` | `/opt/flutter/bin/flutter`（**存在**） |
| `timeout 8 flutter --version` | 8 秒无任何输出，被超时终止 |
| `timeout 45 flutter --version` | 45 秒无输出 |
| `flutter build windows --help` | 45 秒无输出 |
| `flutter create --platforms=windows /tmp/_flprobe` | 未生成 `windows/` 目录 |
| 后台工具链探测任务 | 运行 8 分钟未完成，被主动终止 |

对照：`dart pub get --offline`、`dart analyze`、`dart run`、`dart format` 均正常。结论是 **Dart SDK 可用，Flutter CLI 在本机不可用/卡死**；且即使 Flutter 可用，Windows 桌面构建仍必须在 Windows 主机上完成（需 MSVC + Windows SDK）。

### 2.4 网络与凭据

| 主机 | `curl --max-time 6/8` 结果 |
|---|---|
| `api.github.com` | **200**（约 5.1s） |
| `uploads.github.com` | 302（0.64s） |
| `objects.githubusercontent.com` | 404（1.42s，主机可达） |
| `codeload.github.com` | 301（0.70s） |
| `github.com` | **超时**（8s 无完整响应；`140.82.114.3` 收到 101,328 bytes 后被截断） |
| `maven.google.com` | 连接超时 |

`/etc/hosts` 有 `140.82.112.4 github.com`，`~/.user_hosts` 另存 `140.82.113.3`；TCP 443 对多个 GitHub IP 均可达，但 HTTPS GET 到 `github.com` 不稳定。**这会影响 `git push`（走 github.com），但不影响 `gh api` / Release 上传（走 api/uploads/objects）**。

| 凭据 | 状态 |
|---|---|
| Android 发布密钥 | `/root/.xqly-keys/xqly.jks` 存在，证书 SHA-256 `E1:E8:ED:C5:...:C2:1C:05`（= `e1e8edc5…c21c05`） |
| GitHub CLI 登录 | `gh auth status` → **未登录**；`git-credential-helper get` 无凭据返回 |
| Windows 代码签名证书 | **缺失**（无 PFX / 无 `signtool.exe`） |
| 云端同步凭据 / 第三方 AI Key | **无，且不虚构** |

---

## 3. 旧 Android 基线（v2.0.0，真实）

| 项 | 实测值 | 证据 |
|---|---|---|
| 提交 | `a371377` (2026-11-05) | `git show --no-patch` |
| 版本 | `versionName=2.0.0` / `versionCode=5` | `git show a371377:AndroidManifest.xml` |
| 构建链 | 零 Gradle，编号 `[0/9]…[9/9]` 共 10 个步骤回显（README 称「9 步」） | `git show a371377:build.sh \| grep -c '^echo "=== \['` → 10 |
| 测试 | **123 个用例**（JVM 104 + Node 19） | v2.0.0 README 与 `tests/` |
| 内容清单 | 227 个文件 / 10,526,099 bytes | v2.0.0 README |
| Java 逻辑类 | `src/cn/xqly/app/core/` 下 15 个类（README 写 14，实测 15） | `ls` |
| 签名 | 与 v1.1.0 起一致，可覆盖升级 | 证书指纹钉扎 |
| 回滚点 | tag `pre-final-upgrade` | `git tag --list` |

**基线复跑结果（本轮）**：旧测试套件仍全部通过 —— JVM `104 passed, 0 failed`、Node `19 passed, 0 failed`；`build.sh` 完整 12 步门禁通过并产出正式签名 APK。也就是说，v2.0.1 的改造**没有破坏任何既有测试**。

当前规模：Java 25 个文件 / 4,943 行；Dart 19 个 `.dart` 文件；C++ 11 个 `.cpp`/`.h`；`assets/` 229 个文件；`MANIFEST.json` 228 条目 / 10,531,971 bytes。

---

## 4. 技术决策：为什么不做全量 Flutter / KMP 重写

用户要求「优先 Flutter，共享 Dart 业务层与响应式界面，Windows 用 Win32 原生窗口，Android 用原生 Activity；如果 Flutter 迁移成本不合理，再评估 KMP 并提出证据；不得未经评估就重写」。

### 4.1 评估结论

| 方案 | 证据 | 结论 |
|---|---|---|
| 全量 Flutter UI 重写 | ① 本机 `flutter` CLI 卡死（2.3 节 5 项探测）；② Flutter Windows 构建必须 Windows 主机，本机无 MSVC/SDK；③ 现有 Android 是 25 个 Java 文件、已真实验证的零 Gradle 链路，重写会丢弃可信更新链、离线镜像映射、WebView 桥绑定等已修复的 P0 问题；④ 用户同时要求「Windows 用 Win32 原生窗口、Android 用原生 Activity」，这与 Flutter 接管整个窗口/视图树存在架构冲突 | **不采用**。迁移成本不合理，且会违反「不为框架纯度重写稳定且正确的原生代码」的约束 |
| Kotlin Multiplatform | ① 本机无 Kotlin 编译器、无 KMP/Compose 依赖缓存；② `maven.google.com` 连接超时，依赖无法解析；③ 与「共享 Dart 业务层」的偏好不符；④ v2.0.0 阶段已评估过 Kotlin/Compose/Material 3 全量重写同样不可行 | **不采用**（有 2.4 节网络证据 + v2.0.0 `docs/MIGRATION.md` 结论） |
| **纯 Dart 共享核心 + 各平台原生外壳** | ① Dart SDK 2.17.0 可用且 `pub get --offline` 可构建；② 零第三方依赖，双平台都能消费同一份规则；③ 保留 Android 已验证链路不回退；④ Windows 外壳可以是**真正的 Win32 原生窗口**而非浏览器壳；⑤ 设计令牌由一处 Dart 真源生成三份产物，天然满足「最大化复用」 | ✅ **采用** |

### 4.2 代价（如实说明）

- 共享核心是**平台无关逻辑**，不是共享 UI。UI 仍由各平台原生实现，因此存在两套视图代码。
- Windows 外壳通过版本化平台通道消费 Dart 规则；当前 C++ 侧 `security_core.cpp` 实现了与 Dart `bridge_protocol.dart` 等价的校验子集，**规则子集是重复的**，需在后续版本把 C++ 校验改为由共享核心生成的规范表驱动。这一点列入风险矩阵。
- 没有 Flutter 的响应式 UI 框架，桌面三栏布局、手机/平板/折叠屏自适应分别由原生代码处理。

### 4.3 明确没有做的三件事

1. 没有为了「原生 Windows」而把 Android 现有 WebView 应用整体塞进一个浏览器窗口壳。
2. 没有删除或盲重写已稳定的 Android Java 代码。
3. 没有把 `flutter create` 失败包装成「Flutter 工程已建立」。

---

## 5. 可复用模块清单

| 模块 | 位置 | 复用方式 | 验证证据 |
|---|---|---|---|
| 严格 URL 白名单 | `core/UrlPolicy.java` → `shared/lib/src/url_policy.dart` | Dart 规则为双平台真源；Android 仍用 Java 版 | Dart 152 测试含白名单用例；JVM 104 |
| 版本化桥协议 | `core/BridgeProtocol.java` → `bridge_protocol.dart` + `windows/runner/core/security_core.cpp` | 同一校验链（版本/来源/类型/参数/权限/幂等） | 桥测试；C++ 34 测试含桥校验 |
| 内容清单校验 | `core/ContentManifest.java` → `content_manifest.dart` | 防路径穿越/ZIP 炸弹/版本回退 | 双端测试 |
| 可信更新链判定 | `core/UpdateVerdict.java` → `update_verdict.dart` | 发布者/资产名/主机/大小/SHA-256/签名/非降级 | 双端测试 |
| 版本策略 | `core/VersionPolicy.java` → `version_policy.dart` | `versionCode` 优先、拒绝预发布与降级 | 双端测试 |
| 设计令牌 | `core/DesignTokens.java` → `design_tokens.dart` | **一处真源**生成 `design-tokens.json`/`.css`，Android WebView 与 Windows 外壳都读同一份语义 | `gen_tokens.dart --check`；`build.sh` 第 11 步 APK 内字节比对 |
| 功能开关 | `core/FeatureFlags.java` → `feature_flags.dart` | 默认关闭项不允许覆盖打开 | 双端测试 |
| 全状态 UI | `core/AppState.java` → `app_state.dart` | 13 种状态文案与可恢复状态机 | 双端测试 |
| 游戏规则 | `core/GameRules.java` → `game_rules.dart` + `assets/game-engine.js` | 规则/渲染分离，确定性 seed | JVM/Node/Dart 三套测试 |
| 本地检索 | `core/SearchIndex.java` → `search_index.dart` | CJK 单字/bigram、加权、高亮 | 双端测试 + 真实索引互操作测试 |
| 离线镜像映射 | `MainActivity.shouldInterceptRequest` | **原样保留**，Windows 侧仅消费同一套资源约定 | 12 步门禁 + APK 内清单比对 |
| 自动更新实现 | `Updater.java` / `UpdateCenter.java` | 原样保留，判定规则由 Dart 版对齐 | 既有发布链 v1.1.0→v2.0.1 |
| 本地数据隔离 | `PrefsStore.java` / `UserData.java` | 原样保留；Dart 侧 `sync_package.dart` 定义可同步字段白名单 | JVM 测试 + 同步包测试 |
| SHA-256 | `sha256.dart`（新增） | 纯 Dart FIPS 180-4，供同步包/清单/更新链共用 | 已知向量测试 |

---

## 6. 必须替换 / 必须新增的模块

| 类别 | 模块 | 原因 |
|---|---|---|
| 新增 | `shared/` 16 个纯 Dart 模块 | 建立双平台唯一业务真源，含同步包、合并规则、传输接口形状、SHA-256 |
| 新增 | `shared/tool/gen_tokens.dart` | 设计令牌一处生成三份产物，避免双平台漂移 |
| 新增 | `windows/runner/core/`（C++17 平台无关核心） | 桌面窗口状态、多标签、快捷键、协议、桥、令牌加载；需可在 Linux 编译测试 |
| 新增 | `windows/runner/win32/`（Win32 外壳 5 个源文件） | 原生窗口、消息循环、DPI/多显示器、输入、系统集成；**Android 无对应物** |
| 新增 | `windows/packaging/`（MSIX / AppInstaller / Inno Setup / 便携规范 / 签名脚本） | 签名安装版 + 便携 ZIP 两类产物 |
| 新增 | `.github/workflows/ci.yml` / `release.yml` | 双平台 CI 与 Release 草稿，Windows 产物只能在 `windows-latest` 生成 |
| 新增 | `tools/gen_release_assets.py` | 从真实产物生成 `SHA256SUMS.txt` 与 SPDX 2.3 SBOM |
| 新增 | 合规文档（`PERMISSIONS.md` / `THIRD_PARTY.md` / `SBOM.md`）与双平台审计/验收文档 | 满足发布合规与「真实验收报告」要求 |
| 替换 | 自有界面品牌表述 | 清除 Apple / macOS / iOS 模仿性描述，统一「星穹玻璃」；自有 CSS 字体栈改中性 |
| 替换 | `build.sh` 9 步 → 12 步门禁 | 增加 Dart 静态分析、令牌生成/校验、Dart 152 测试、APK 内令牌字节比对 |
| 替换 | 版本号 6 处真源 | `2.0.0/5` → `2.0.1/6`，新增 `windowsVersion=2.0.1.0` 与 `resource.h` 四段版本 |
| 保留但加约束 | `docs/全站内容档案.html`、`assets/archive.html`、`assets/library.css` 字体栈 | 去掉厂商字体回退标识；站点镜像 `assets/site/**` 保持原始字节（见第 7 节） |

---

## 7. 平台差异与风险矩阵

| # | 风险 / 差异 | 影响 | 当前处置 | 残余风险 |
|---|---|---|---|---|
| R1 | **无 Windows 主机工具链** | 无法编译 Win32、生成 MSIX/ZIP、签名 | 源码完整 + 平台无关核心在 Linux 真实编译测试；CI `windows-latest` 负责产物 | ⚠ 需首次 CI 运行验证 MSVC 编译与资源脚本 |
| R2 | **无正式代码签名证书** | 安装版无法以可信发布者分发 | `sign.ps1` 无证书时输出 `UNSIGNED`；MSIX Publisher 明示为测试签名主体；Inno Setup 备选 | ⚠ 需运营团队提供证书并替换 Publisher |
| R3 | 本机 `javac 20` vs CI `javac 17` | 编译产物可能不一致 | `-source 8 -target 8`，CI 锁定 Temurin 17 | 低，需以 CI 结果为准 |
| R4 | **`github.com` HTTPS 不稳定** | `git push` 可能超时 | `scripts/publish.sh` 5 次重试 + 每次回读远端 HEAD；`gh api` 走可达的 `api.github.com` | ⚠ 推送可能需重试或改走 API |
| R5 | 共享核心是逻辑而非 UI | 两套视图代码、校验规则在 C++ 侧有子集重复 | 设计令牌已统一；C++ 桥校验与 Dart 规则通过同一批用例对齐 | 中：建议后续改为规范表驱动 |
| R6 | 站点镜像残留厂商技术标识 | 与「清理品牌词」要求存在字面冲突 | 自有源码/文档/页面全部清理；镜像保持原始字节以保证离线渲染；在 `THIRD_PARTY.md` 与 `PRIVACY.md` 如实说明 | 低（内容而非自有界面） |
| R7 | `usesCleartextTraffic=true` / `allowBackup=true` | Manifest 层面权限偏宽 | 代码层已拒绝明文站点与混合内容；在 `PERMISSIONS.md` 标注并给出下一版建议 | 中：建议下一版评估收紧 |
| R8 | 无真机 / 触屏 / 多显示器设备 | DPI 100–300%、触屏、性能数字无法验证 | 逻辑层 34 个单元测试覆盖窗口状态与显示器拔除；**不提供任何编造的性能数字或截图** | ⚠ 需真机验收清单（见验收报告） |
| R9 | 云端同步无凭据 | 无法演示端到端云同步 | 默认关闭显示「未配置」，只做本地包导入导出 | 低（设计如此） |
| R10 | APK 非字节级可复现 | 相同源码重复签名哈希不同 | 内容清单幂等；Release 一律以当次 `SHA256SUMS.txt` 为准 | 低，已如实披露 |

---

## 8. 迁移映射摘要

完整逐模块映射、全量 Flutter 迁移的阶段性成本、人工补齐清单与回滚策略见 [MIGRATION_MAP.md](MIGRATION_MAP.md)。摘要：

```
Android core/*.java (15)  ──映射──▶  shared/lib/src/*.dart (16)
        │                                    │
        │ 原样保留                            ├─▶ assets/design-tokens.css  (Android WebView)
        ▼                                    ├─▶ shared/design-tokens.json (测试/工具)
MainActivity / Updater / WebView            └─▶ windows/runner/resources/design-tokens.json (Win32)
        │
        └─ 保留已验证链路，不重写

windows/runner/core  ──Linux 真实编译 + 34 测试──▶  windows/runner/win32 ──CI windows-latest──▶ EXE/MSIX/ZIP
```

---

## 9. 明确未做的事与原因

1. **没有生成 `xqly.exe` / MSIX / 便携 ZIP**：本机无 MSVC / Windows SDK / `makeappx.exe`，不伪造二进制。
2. **没有做全量 Flutter UI 重写**：CLI 卡死 + 无 Windows 主机 + 与「原生窗口」要求冲突，证据见 2.3 / 4.1。
3. **没有做 Kotlin/Compose/Material 3 重写**：无 Kotlin 编译器与依赖、Maven 不可达，且用户约束「不为框架纯度重写稳定代码」。
4. **没有真机/触屏/多显示器/性能实测**：无 `adb` / `emulator` / Windows 真机 / 触屏设备；不编造数字，不用模拟器截图冒充。
5. **没有虚构云端服务器、账号体系、AI Key 或第三方付费服务**：云端同步只定义接口形状。
6. **没有伪造 Windows 代码签名**：签名脚本在无证书时明确返回 `UNSIGNED`。
7. **没有把编译成功当成功能完成**：所有「已验证」项都对应具体命令与测试数字，见 [ACCEPTANCE_2.0.1.md](ACCEPTANCE_2.0.1.md)。
