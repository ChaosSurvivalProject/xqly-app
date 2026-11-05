# v2.0.1 双平台改造 · 真实验收报告

验收时间 2026-11-05 15:27 UTC（最终全量复验）· 产物 `release/` · 审计报告见 [AUDIT_2.0.1.md](AUDIT_2.0.1.md)

> **本报告只记录实际执行过的命令及其输出。** 未做的测试、未产出的二进制、未测量的性能数字一律标注为「未验证」，不以编译成功、本地可运行或源码存在冒充完成。

---

## 0. 验收结论

| 结论项 | 判定 |
|---|---|
| Android 正式签名 APK | ✅ **可交付**：12 步门禁全绿、正式发布密钥签名、版本与签名证书正确 |
| 双平台共享核心 / Windows 平台无关核心 | ✅ **可交付**：309 个自动化测试全绿，设计令牌三份产物字节一致 |
| Windows 安装版 / 便携版二进制 | ⚠ **未产出**：本机无 MSVC / Windows SDK / 签名证书，由 CI `windows-latest` 生成，**不伪造** |
| Windows 真机 / 触屏 / DPI / 性能 | ❌ **未验证**：无设备，不提供任何数字或截图 |
| 全量 Flutter / KMP UI 重写 | ❌ **未实施**：评估证据不支持，见 [AUDIT 第 4 节](AUDIT_2.0.1.md) |

**总体判定**：本版本是**经过真实构建与测试验证的双平台工程基线 + 已发布的 Android 正式产物**，不是「已在真机上跑通的双平台成品」。

---

## 1. 验收环境（实测）

| 项 | 值 |
|---|---|
| OS | Linux 沙箱（Ubuntu 系），**非 Windows** |
| Python / Node / Dart | 3.11.1 / v22.13.1 / 2.17.0 |
| javac | **OpenJDK 20**（CI 锁定 Temurin 17，编译目标 `-source 8 -target 8`） |
| CMake / g++ | 3.28.3 / 13.3.0 |
| Android SDK | `build-tools/35.0.0`、`platforms/android-35` |
| 缺失 | `msbuild`、`cl.exe`、`signtool.exe`、`makeappx.exe`、`wine`、mingw、`adb`、`emulator` |
| Flutter | `/opt/flutter/bin/flutter` 存在，但 `--version` 8s/45s 无输出，`create --platforms=windows` 未生成工程 |
| 网络 | `api.github.com` 200、`uploads.github.com` 302、`github.com` 超时、`maven.google.com` 超时 |
| 签名密钥 | `/root/.xqly-keys/xqly.jks` 存在（仓库外），证书 SHA-256 `e1e8edc5…c21c05` |
| 其他凭据 | `gh auth status` 未登录；无 Windows 代码签名证书；无云端同步/AI 凭据 |

---

## 2. 需求逐项验收

| # | 要求 | 状态 | 证据 |
|---|---|---|---|
| 1 | 审计现有仓库 | ✅ | `git ls-files` 291 文件、旧基线 `a371377` 逐项核对，见 [AUDIT 第 1/3 节](AUDIT_2.0.1.md) |
| 2 | 运行旧 Android 构建与测试，记录真实基线 | ✅ | 旧 123 用例（JVM 104 + Node 19）在 v2.0.1 下仍全绿；旧 9 步链 → 新 12 步链 |
| 3 | 事实证据 / 可复用模块 / 必须替换模块 / 平台差异与风险 / 迁移映射 | ✅ | [AUDIT 第 2/5/6/7/8 节](AUDIT_2.0.1.md)、[MIGRATION_MAP.md](MIGRATION_MAP.md) |
| 4 | 建立双平台工程（各平台保留原生外壳） | ✅ | `shared/`（纯 Dart）+ `windows/`（Win32）+ 保留 `src/`（Android 原生 Java） |
| 5 | 清理安全与版权风险 | ✅ | 清除自有界面品牌词、字体栈中性化、`NOTICE.md` 授权划分、CI 品牌/敏感文件扫描；镜像技术标识在 `THIRD_PARTY.md` 如实说明 |
| 6 | 实现共享核心 | ✅ | 16 个纯 Dart 模块、零第三方依赖、`dart pub get --offline` 可构建 |
| 7 | 完成 Windows 原生体验 | ⚠ **源码级** | Win32 外壳 5 个源文件 + 打包配置齐备；平台无关核心已编译并测试；**Win32 目标未在本机编译** |
| 8 | 完成 Android 适配 | ✅ | 版本 `2.0.1/6`、设计令牌接入三个内置页、`UrlPolicy` 白名单扩展、正式签名 APK |
| 9 | 实现本地优先同步 | ✅ | `sync_package.dart` / `sync_merge.dart` / `sync_transport.dart`，含校验和、Lamport 时钟、墓碑、冲突逐项确认；云端默认「未配置」 |
| 10 | 重做游戏交互 | ⚠ **部分** | 保留 v2.0.0 方块合成 2.0，新增 Dart 规则引擎与 Windows 键鼠/触屏输入映射；**未重做游戏 UI 交互设计** |
| 11 | 优化性能 | ❌ **未做** | 无真机与测量环境，不提供冷启动/帧时间/内存数字，也不编造优化结论 |
| 12 | 构建并验证三类产物 | ⚠ **1/3** | Android APK ✅；Windows MSIX / 便携 ZIP 需 CI，见第 5 节 |
| 13 | 跑全部测试 | ✅ | 309/309 通过，见第 3 节 |
| 14 | 输出真实验收报告 | ✅ | 本文 |
| 15 | GitHub Actions 双平台 CI 与 Release 草稿 | ✅ 配置齐备 | `ci.yml`（5 jobs）、`release.yml`（4 jobs）；YAML 语法实测通过；Release 创建状态见第 9 节 |
| 16 | 隐私政策 / 权限说明 / 第三方依赖清单 / 软件清单 | ✅ | `PRIVACY.md`、`PERMISSIONS.md`、`THIRD_PARTY.md`、`dual-platform/SBOM.md` + `release/SBOM.spdx.json` |
| 17 | 无证书/授权时安全降级 + 人工步骤 | ✅ | `sign.ps1` → `SIGN_RESULT=UNSIGNED`；MSIX Publisher 明示测试主体；11 项人工补齐清单见 [MIGRATION_MAP 第 6 节](MIGRATION_MAP.md) |

---

## 3. 测试执行结果（最终全量复验真实输出）

| 套件 | 用例数 | 结果 | 命令 |
|---|---|---|---|
| Dart 共享核心 | 152 | **152 passed, 0 failed** | `dart test/run_tests.dart ..` |
| JVM 逻辑（零 Android 依赖） | 104 | **104 passed, 0 failed** | `bash tests/run-tests.sh` |
| Node 游戏规则引擎 | 19 | **19 passed, 0 failed** | `node tests/game-engine.test.js` |
| C++ 平台无关核心 | 34 | **34 passed, 0 failed** | `cmake -S windows/runner/core -B … && ctest` / 直接运行测试二进制 |
| **合计** | **309** | **309 passed, 0 failed** | — |

其他门禁的真实输出：

```
Analyzing shared... No issues found!                          # dart analyze
设计令牌校验通过（3 个文件，6 套主题映射）                     # gen_tokens.dart --check
MANIFEST.json 校验通过：228 个文件，version=1793890532
search-index.json 校验通过：13 条
  MANIFEST.json / design-tokens.css 字节一致                  # build.sh 第 11 步
E1_PASS 品牌扫描 / E2_PASS 合规文件 / E3_PASS YAML
PASS .github/workflows/ci.yml      jobs: shared-core,android,windows-core,windows-shell,security-scan
PASS .github/workflows/release.yml jobs: validate,android,windows,draft
PASS 版本一致性（六处真源 + 递增） TAG=2.0.1 CODE=6 PREV=v2.0.0(5) COMMA=[2, 0, 1, 0]
ctest（顶层 windows/CMakeLists.txt）: 100% tests passed, 1/1
```

`build.sh` 12 步全部返回 0：`A_RC=0`、`B_RC=0`、`C_RC=0`。

---

## 4. Android 产物校验（真实）

| 项 | 值 |
|---|---|
| 文件 | `dist/星穹旅驿.apk` → `release/xqly-app-v2.0.1.apk` |
| 大小 | 6,661,554 bytes |
| **SHA-256** | `de181ef7f1adb5f9579d8fe6e414bd3ceb82e2978610d6643e04c73dbed38263` |
| 签名 | v2 scheme ✅ / v3 scheme ✅ / 1 signer（v1 未使用，minSdk 24 不需要） |
| 签名证书 SHA-256 | `e1e8edc56e9700a27ab2cd4ba5f45fa0ba1bd9a88e4a261de7ef3684b3c21c05`（与 v1.1.0 起一致，可覆盖升级） |
| `aapt dump badging` | `name='cn.xqly.app' versionCode='6' versionName='2.0.1'`、`sdkVersion:'24'`、`targetSdkVersion:'35'` |
| APK 内关键资源 | `classes.dex` 168,696 B、`assets/game-engine.js` 9,227 B、`assets/MANIFEST.json` 36,967 B、`assets/design-tokens.css` 5,764 B |
| 内容清单 | 228 文件 / 10,531,971 bytes（10.04 MB），`version=1793890532` 幂等未漂移 |

### 可复现性实测（重要）

同一份源码连续构建得到过三个不同的 APK SHA-256：`4624032…`、`61cb744c…`、`de181ef7…`。原因是 `apksigner` 签名含时间戳，**APK 非字节级可复现**；内容清单因 `gen_manifest.py` 的幂等逻辑保持不变。因此 Release 资产**一律以当次 `SHA256SUMS.txt` 为准**，本报告不宣称「相同哈希可复现」。

---

## 5. 发布资产与三类产物状态

`release/` 由 `tools/gen_release_assets.py release/ 2.0.1` 从真实文件生成：

```
生成 2 个资产的摘要与 SBOM：
  de181ef7f1ad  xqly-app-v2.0.1.apk
  de181ef7f1ad  星穹旅驿-2.0.1-android-universal-release.apk
  SHA256SUMS.txt / SBOM.spdx.json（16 个组件）
```

- `sha256sum -c SHA256SUMS.txt` → 两个文件均 `OK`；两份 APK `cmp` 字节相同。
- `SBOM.spdx.json`：SPDX 2.3，`17 packages`（主包 + 16 组件）、`2 files`、`3 relationships`，`created=2026-11-05T15:29:38Z`（`SOURCE_DATE_EPOCH` 固定）。
- SBOM 的 `files[]` **只有 2 个 Android 资产**：因为本机没有 Windows 产物，生成器只扫描真实存在的文件，不创建占位假文件。这正是「不伪造」的体现。

| 产物 | 本机状态 | 生成方 |
|---|---|---|
| Android 正式签名 APK | ✅ 已产出并校验 | `build.sh` + 仓库外发布密钥 |
| Windows 签名 MSIX 安装版 | ❌ 未产出（无 MSVC/makeappx/证书） | `release.yml` → `windows-latest` |
| Windows 便携 ZIP | ❌ 未产出（同上） | `release.yml` → `windows-latest` |

CI 生成 Windows 产物后会重新运行生成器，把三类产物一并纳入 Release 的 `SHA256SUMS.txt` 与 SBOM。

---

## 6. 本轮发现并修复的真实缺陷

这些问题是**在本轮验收复跑中实际触发**的，不是推测；逐项都有失败输出与修复后复验。

| # | 缺陷 | 触发证据 | 修复 |
|---|---|---|---|
| 1 | `ci.yml` 检查 `docs/privacy.md`（小写），实际为 `PRIVACY.md` | `test -f docs/privacy.md` 返回失败，Linux 区分大小写 | 改为循环检查 6 个真实文件，含新增合规文档 |
| 2 | `release.yml` 版本逗号校验拼法错误 | `VERSION_COMMA  2,0,1, 0` 与实际 `2, 0, 1, 0` 不匹配，实测「CI 会失败」 | 改纯 shell 参数展开拼字面量 + `grep -qF` |
| 3 | 上项修复引入的 awk 引号歧义 | `awk: line 1: runaway string constant ", 0}` | 弃用 awk，改 `${TAG%%.*}` 等参数展开 |
| 4 | zsh 把 `$PREV:A` 当作路径修饰符 | `fatal: ambiguous argument '…/v2.0.0ndroidManifest.xml'` | 显式 `"${PREV}:AndroidManifest.xml"` |
| 5 | `release.yml` flow mapping 内嵌 `${{ }}` | PyYAML `ParserError: expected ',' or '}', but got '{'`（line 78） | 改为块样式 `with:\n  sdk: …` |
| 6 | 顶层 `cmake -S windows` 后 `ctest` 找不到测试 | `No tests were found!!!` | 顶层 `CMakeLists.txt` 增加 `enable_testing()`；复验 `100% tests passed, 1/1` |
| 7 | `README.md` 仍是 v2.0.0 / code 5 / 9 步 / 123 测试 / 液态玻璃 | 与 `AndroidManifest.xml`、`build.sh` 实测冲突 | 全文重写为 2.0.1 双平台口径，数字与实测对齐 |
| 8 | 自有界面品牌词残留 | `GlassDrawable.java`「液态玻璃」、`README.md`「原生液态玻璃」、自有 CSS/HTML 的 `-apple-system` | 统一「星穹玻璃」+ 中性字体栈；镜像资源保持原字节并如实说明 |
| 9 | `windows/README.md` 指向不存在的 `docs/双平台改造验收.md` | 全仓 Markdown 坏链接扫描 | 指向 `docs/dual-platform/ACCEPTANCE_2.0.1.md` |
| 10 | 改动 `assets/` 后清单哈希失效 | 门禁曾捕获（历史项，本轮遵循「生成先于测试」） | `build.sh` 第 2 步先生成/校验资源，再跑测试 |

---

## 7. 已验证 / 未验证 / 需真机验证

### ✅ 已验证

- 四套测试 309/309、Dart 静态分析、设计令牌三份产物一致性、内容清单与索引校验。
- 12 步构建门禁、APK 内资源字节比对、正式签名与证书指纹、`aapt` 版本信息。
- CI 五类门禁（品牌词、合规文件、敏感文件、YAML、版本六处一致性 + 递增）。
- 发布资产摘要自校验、SPDX 结构、两份 APK 字节一致。
- 全仓 Markdown 本地链接有效性（验收报告创建后复验）。

### ⚠ 未验证（环境限制，不伪造）

- Win32 目标编译（`windows/runner/win32/*.cpp` 未经 MSVC 编译）。
- MSIX / EXE 安装器 / 便携 ZIP 实际生成与安装。
- Windows 代码签名与 RFC3161 时间戳（无证书）。
- `.github/workflows` 在真实 GitHub runner 上的首次执行（本机只做 YAML 与命令级复验）。
- 云端同步端到端（默认「未配置」，设计如此）。

### ❌ 需真机验证（无设备，不提供数字或截图）

- DPI 100/150/200/300%、多显示器与显示器拔除、窗口状态持久化。
- 触屏 / 触控笔 / 游戏键鼠映射、托盘 / 通知 / 开机启动 / 协议 / 文件关联。
- WebView2 Runtime 缺失时的降级提示。
- 安装版与便携版并存、卸载不删用户数据。
- 冷启动时间、帧时间、内存占用、离线包加载耗时。
- Android 真机覆盖升级（v2.0.0 → v2.0.1）与自动更新全流程。

---

## 8. 严格禁止事项自查

| 用户禁令 | 自查结果 |
|---|---|
| 不把伪代码 / 示意代码 / 待办清单当作完成 | ✅ 交付为可编译源码 + 已签名 APK；未完成项在 §7 明列 |
| 不把「编译成功」当作「功能完成」 | ✅ 每条「已验证」都对应具体命令与测试数字 |
| 不用模拟器截图冒充真机证明 | ✅ 本报告无任何截图或真机性能数字 |
| 不伪造签名证书、Token、凭据 | ✅ 无证书 → `UNSIGNED`；密钥在仓库外；CI 从 Secrets 读取 |
| 不虚构服务器 / 第三方 AI Key / 付费服务 | ✅ 云端同步默认「未配置」，只定义接口形状 |
| 不未经评估就重写 | ✅ Flutter / KMP 评估证据见 [AUDIT 第 4 节](AUDIT_2.0.1.md) |
| 不提交 keystore / Token / 密码 | ✅ CI 敏感文件扫描通过；`.gitignore` 排除 `*.jks` / `dist/` / `release/` |
| 不把本地开发机正常运行包装成「已完成」 | ✅ §0 明确总体判定为「工程基线 + Android 产物」，非双平台真机成品 |

---

## 9. 发布状态

`release/` 已生成可直接上传的 5 个文件（2 个 APK、`SHA256SUMS.txt`、`SBOM.spdx.json`、`RELEASE_NOTES_2.0.1.md`），摘要自校验通过。`ci.yml` / `release.yml` 已就位并通过 YAML 与命令级复验。

GitHub Release 创建、源码推送与资产端到端下载校验的结果，见最终发布记录；`release.yml` 的 `draft` job 在检测到 Release 已存在时会跳过创建，避免 CI 用测试签名 APK 覆盖正式签名资产。

---

## 10. 人工补齐与回滚

11 项人工补齐清单（Windows 代码签名、首次 CI 编译、真机验收、性能基准、Release Secrets、AppInstaller 更新源、云端同步、Manifest 收紧、C++ 校验去重、内容脱敏、镜像标识清理）见 [MIGRATION_MAP 第 6 节](MIGRATION_MAP.md)。

回滚点：git tag `pre-final-upgrade`、提交 `23cc277`（v2.0.0）；Android 直接用上一版 APK 覆盖安装即可回退（同一签名证书，**不要卸载**）；内容包校验失败只删临时文件；Release 草稿可直接删除。详见 [MIGRATION_MAP 第 7 节](MIGRATION_MAP.md)。
