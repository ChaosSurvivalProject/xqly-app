# 双平台迁移映射与人工补齐清单

配套文档：[AUDIT_2.0.1.md](AUDIT_2.0.1.md)（事实证据与技术决策）· [ACCEPTANCE_2.0.1.md](ACCEPTANCE_2.0.1.md)（真实验收结果）

---

## 1. 映射总览

```
                    ┌─────────────────────────────────────────────┐
                    │  shared/ 纯 Dart 双平台共享核心（唯一业务真源）│
                    │  16 模块 · 零第三方依赖 · 152 测试            │
                    └───────┬─────────────────────────┬───────────┘
                            │ 规则映射                  │ 令牌生成
              ┌─────────────▼──────────┐   ┌──────────▼────────────────────┐
              │ Android（保留已验证链路）│   │ 三份令牌产物（字节受门禁校验） │
              │ Java core 15 类 + WebView│   │ assets/design-tokens.css      │
              │ → 正式签名 APK ✅        │   │ shared/design-tokens.json     │
              └────────────────────────┘   │ windows/.../design-tokens.json│
                                            └──────────┬────────────────────┘
                            ┌───────────────────────────▼─────────────┐
                            │ Windows 原生外壳（Win32）                │
                            │ core/ 平台无关 C++17（Linux 已编译+34测试）│
                            │   ↓ CI windows-latest / MSVC              │
                            │ win32/ → xqly.exe → MSIX + 便携 ZIP ⚠     │
                            └──────────────────────────────────────────┘
```

⚠ = 需 Windows 主机，本机不伪造。

---

## 2. Android `core/*.java` → `shared/lib/src/*.dart` 逐模块映射

| # | Java 类 | Dart 模块 | 映射方式 | 备注 |
|---|---|---|---|---|
| 1 | `AppIdentity.java` | `identity.dart` | 语义对齐 | Dart 侧为双平台唯一真源（含 `windowsVersion`、OTA 资产名规则） |
| 2 | `AppState.java` | `app_state.dart` | 语义对齐 + 扩展 | Dart 侧新增 `syncConflict` / `syncCancelled` 状态 |
| 3 | `BridgeProtocol.java` | `bridge_protocol.dart` | 语义对齐 | 校验链一致：长度→JSON→版本→origin→页面可信→同源→类型→参数→危险键→权限→幂等 id |
| 4 | `ContentManifest.java` | `content_manifest.dart` | 语义对齐 | `PACK_MAX_SINGLE_BYTES` 统一为 32 MB；空文件合法（镜像空 JS 占位） |
| 5 | `DesignTokens.java` | `design_tokens.dart` | **升级为真源** | Dart 生成 CSS / JSON 三份产物，不再由 Java 单独维护 |
| 6 | `FeatureFlags.java` | `feature_flags.dart` | 语义对齐 | 默认关闭项不允许用户覆盖打开 |
| 7 | `GameRules.java` | `game_rules.dart` | 语义对齐 | 与 `assets/game-engine.js` 三套测试（JVM/Node/Dart）对齐 |
| 8 | `MiniJson.java` | `foundation.dart` | 语义对齐 + 强化 | Dart 版拒绝尾随内容、控制字符、NaN/Infinity、超深嵌套；键按字典序确定性序列化 |
| 9 | `SearchIndex.java` | `search_index.dart` | 语义对齐 | CJK 单字/bigram、标题/标签加权、高亮预览 |
| 10 | `SigningCert.java` | `identity.dart`（常量） | 合并 | 平台特定取证书字节的逻辑留在 Android，钉扎值进共享真源 |
| 11 | `UpdateVerdict.java` | `update_verdict.dart` | 语义对齐 | 不因 URL 来自 GitHub 就信任 APK |
| 12 | `UrlPolicy.java` | `url_policy.dart` | 语义对齐 + 扩展 | `localResources` 增加 `design-tokens.css` |
| 13 | `VersionPolicy.java` | `version_policy.dart` | 语义对齐 | `versionCode` 优先于 tag，防 tag 相同但 code 回退 |
| 14 | `UserData.java` | `sync_package.dart`（字段白名单） | 拆分 | 存储实现留在平台侧；可同步字段由共享核心定义 |
| 15 | `UserPrefs.java` | 平台侧保留 | 不迁移 | 纯键值接口，Android `SharedPreferences` / Windows 文件各自实现 |
| — | （新增） | `sha256.dart` | 新增 | 纯 Dart FIPS 180-4，供同步包/清单/更新链共用，避免第三方依赖 |
| — | （新增） | `sync_package.dart` / `sync_merge.dart` / `sync_transport.dart` | 新增 | 本地优先同步包、冲突合并、可选云端接口形状 |

---

## 3. 平台外壳职责矩阵

| 能力 | Android | Windows | 共享核心负责 |
|---|---|---|---|
| 窗口 / Activity | `MainActivity`（`singleTop`） | `wWinMain` + `HWND` + 消息循环 | — |
| 网页内容承载 | 系统 WebView（`shouldInterceptRequest` 离线镜像） | 可选系统 WebView2（内容区） | URL / 桥 / 清单规则 |
| 导航 | 侧边药丸 + 底部 Dock（手机）/ 竖向 rail（平板） | 三栏布局 + 多标签 + 快捷键 | 功能开关、全状态文案 |
| 视觉 | 「星穹玻璃」自绘 `GlassDrawable` / `GlassDock` | 原生窗口 + 共享设计令牌 | 设计令牌（6 套主题） |
| 自适应 | 最短边缩放、横竖屏、折叠屏 | DPI 100–300%、多显示器、窗口状态持久化 | 平台无关窗口状态规则（C++ core 已测） |
| 更新 | `Updater` / `UpdateCenter`（应用内安装 APK） | 设置页检查 + 引导下载（不静默替换 EXE） | 更新链判定规则 |
| 同步 | 导入/导出 `.xqlypack` | 文件关联 `.xqlypack`、协议 `xqly://app/sync` | 同步包、合并、传输约束 |
| 数据 | `SharedPreferences`（`xqly_user` / `xqly_cache`） | `Data\` / `%LOCALAPPDATA%\XQLY` | 可同步字段白名单 |
| 分发 | 正式签名 APK（✅ 本机产出） | MSIX + 便携 ZIP（⚠ CI 产出） | 版本号与资产命名规则 |

---

## 4. 双平台数据契约

| 契约 | 真源 | 产物 / 消费方 | 一致性门禁 |
|---|---|---|---|
| 设计令牌 | `shared/lib/src/design_tokens.dart` | `assets/design-tokens.css`（Android）、`windows/runner/resources/design-tokens.json`（Win32）、`shared/design-tokens.json`（工具/测试） | `dart tool/gen_tokens.dart .. --check`；`build.sh` 第 11 步 APK 内字节 `cmp` |
| 版本 | `AndroidManifest.xml` / `MainActivity.java` / `build.sh` / `shared/lib/src/identity.dart` / `windows/runner/resources/resource.h` | 双平台产物 | `build.sh` 第 0 步 + `release.yml` validate job 六处 `grep -qF` |
| 内容清单 | `tools/gen_manifest.py` | `assets/MANIFEST.json`，Android 与 Dart 核心共同校验 | `gen_manifest.py --check`；APK 内字节比对 |
| 桥消息 | `shared/lib/src/bridge_protocol.dart` | Android `WebBridge`、Windows `security_core.cpp` | 同一批用例在 Dart 与 C++ 测试中各跑一遍 |
| 同步包 | `sync_package.dart` / `sync_merge.dart` | 双平台导入导出 | Dart 152 测试含截断/篡改/冲突用例 |
| 更新链 | `identity.dart` / `version_policy.dart` / `update_verdict.dart` | Android `Updater`；Windows 设置页 | 钉扎发布者 + 资产名 + 主机 + SHA-256 + 证书 + 非降级 |

---

## 5. 若要全量迁移到 Flutter：阶段性成本与证据

用户要求「优先 Flutter；如迁移成本不合理，再评估 KMP 并提出证据；不得未经评估就重写」。评估结论见 [AUDIT 第 4 节](AUDIT_2.0.1.md)。若未来具备 Windows 主机 + 可用 Flutter SDK + 真机环境，建议按下列阶段推进，**每一阶段都必须先跑通并保留回滚点**：

| 阶段 | 内容 | 前置条件 | 可回退性 |
|---|---|---|---|
| F0 | 在 Windows 主机验证 `flutter doctor -v`、`flutter create --platforms=windows` 能产出可编译工程 | Windows 主机 + VS2022 + Flutter SDK | 不改主仓库 |
| F1 | 把 `shared/` 作为 Flutter 项目的纯 Dart package 直接复用（**零改动**） | F0 | 高 |
| F2 | 用 Flutter 实现**一个**非关键页面（如设置页）作为插件式嵌入，验证平台通道 | 双端可运行 | 高 |
| F3 | 逐个迁移业务页面，每迁移一个保留旧原生实现作为开关后的回退路径 | 双端真机 | 中 |
| F4 | 迁移导航与窗口管理；Windows 侧需自定义 `Window` 尺寸/多窗口，Flutter 默认单窗口 | 桌面窗口方案定型 | 低 |
| F5 | 全量切换、删除旧实现、重跑全部安全与更新链测试 | 全部功能对齐 | 低 |

**当前不启动 F0 的证据**：`flutter --version` / `flutter build windows --help` 45s 无输出、`flutter create --platforms=windows` 未生成目录、后台探测 8 分钟未完成；且本机无 MSVC/Windows SDK，Flutter Windows 构建无法在本机完成。盲目 `flutter create` 只会得到一个无法编译的空壳，属于用户明确禁止的「把伪代码/半成品包装成已完成」。

**KMP 备选评估**：本机无 Kotlin 编译器、无 KMP/Compose/Material 3 依赖缓存，`maven.google.com` 连接超时（[AUDIT 2.4](AUDIT_2.0.1.md)）；且与「共享 Dart 业务层」的偏好冲突。v2.0.0 阶段已产出 `docs/MIGRATION.md` 的 Kotlin/Compose 迁移蓝图，本版本不重复启动。

---

## 6. 人工补齐清单（需运营团队 / Windows 主机执行）

> 以下每一项都是**本机环境无法完成**或**需要合法凭据**的事项。本仓库不伪造证书、不伪造服务器、不伪造真机数据。

| # | 事项 | 所需环境 / 凭据 | 具体步骤 | 验收方法 |
|---|---|---|---|---|
| M1 | Windows 代码签名 | 受信任代码签名证书（PFX + 口令） | ① 把证书加入 CI Secrets `WINDOWS_SIGNING_PFX` / `WINDOWS_SIGNING_PASSWORD`；② 把 `AppxManifest.xml` 的 `Publisher` 改为证书 Subject（完全一致）；③ 运行 `windows/packaging/sign.ps1` | `signtool verify /pa /v` 通过；证书链可信；RFC3161 时间戳存在；Release 不再标注 UNSIGNED |
| M2 | 首次 Windows CI 编译 | `windows-latest` runner | 推送代码触发 `ci.yml`；若 MSVC 报错，重点检查 `version.rc` / `app.manifest` / 中文源码 `/utf-8` / 资源路径 | `windows-shell` job 绿灯，`smoke.ps1` 通过，`xqly.exe` 作为 artifact 产出 |
| M3 | Windows 真机验收 | Windows 10 1809+ 真机、多显示器、触屏/触控笔设备 | 逐项走查：DPI 100/150/200/300%、显示器拔除后窗口可见性、多标签与快捷键冲突、托盘/通知、开机启动默认关闭、协议与 `.xqlypack` 关联、WebView2 缺失时的降级提示、安装版与便携版并存 | 填写真机验收表；**禁止用模拟器截图冒充** |
| M4 | 性能基准 | 同 M3 | 记录冷启动、帧时间、内存、离线包加载耗时 | 真实数值 + 设备型号 + 系统版本；本机不提供任何数字 |
| M5 | Release 流水线完整跑通 | GitHub Secrets：`ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`（Windows 证书见 M1） | 打 tag `v2.0.1` 触发 `release.yml`；确认 `draft` job 生成三类资产的 `SHA256SUMS.txt` / SBOM 并创建**草稿** | `gh release view v2.0.1 --json isDraft,assets`；下载后 `sha256sum -c` 通过；`apksigner verify --print-certs` 指纹匹配 |
| M6 | AppInstaller 更新源 | 可公开访问的 HTTPS 主机 | 把 `windows/packaging/msix/appinstaller.xml` 的 URI 模板替换为真实地址，配置 `.appinstaller` 与 MSIX 同源 | 客户端设置页不再显示「未配置更新源」 |
| M7 | 云端同步（可选） | 自建服务端 + 鉴权方案 | 实现 `/v1/sync/changes`、`/v1/sync/items/{id}`、`/v1/sync/session`；在客户端填入地址并保持默认关闭 | 端到端双设备同步 + 冲突逐项确认；**不得虚构服务器地址或凭据** |
| M8 | 收紧 Android Manifest | Android 真机回归环境 | 评估把 `usesCleartextTraffic` / `allowBackup` 改为 `false`，或补 `dataExtractionRules` | 离线镜像、在线浏览、更新安装全部回归通过 |
| M9 | 消除 C++ 桥校验重复 | — | 把 `security_core.cpp` 的校验规则改为由共享核心生成的规范表驱动 | Dart 与 C++ 共用同一批用例全绿 |
| M10 | 内容授权与脱敏 | 运营团队确认 | 如需移除镜像中的运维细节，运行 `python3 tools/redact.py --apply`，再重建索引、清单与 APK | `gen_manifest.py --check` 通过；APK 内清单字节一致；Release 哈希更新 |
| M11 | 镜像品牌标识彻底清理（可选） | 重新爬取环境 | 重跑 `tools/mirror.py` 并替换镜像 CSS 字体栈，重建清单与 APK | 离线渲染与在线一致；`assets/site/**` 不再出现厂商字体回退标识 |

---

## 7. 回滚策略

| 层级 | 回滚点 | 操作 |
|---|---|---|
| 源码 | git tag `pre-final-upgrade`、提交 `23cc277`（v2.0.0） | `git revert` 本版本提交，或 `git checkout 23cc277 -- .` 后重新构建 |
| Android 产物 | 上一版正式签名 APK（v2.0.0，签名证书相同） | 直接覆盖安装即可回退；**不要卸载**（会丢用户数据） |
| 内容包 | `MANIFEST.json` 版本单调递增 + 临时下载 → 校验 → 原子切换 | 校验失败只删临时文件，当前版本完好；版本回退被 `ContentManifest` 拒绝 |
| 同步包 | 导入前完整校验，冲突逐项确认，未确认则返回本地原样 | 中断不会产生半份数据；可丢弃导入包 |
| Windows 产物 | 便携版更新前备份整个 `Data/` 与旧 EXE | 解压回旧 ZIP 或还原备份；安装版保留用户数据目录 |
| Release | `release.yml` 只创建**草稿** | 直接删除草稿，不公开；已推送的 tag 可删除 |

> 任何回滚都**不需要**重新生成签名密钥；Android 发布密钥自 v1.1.0 起保持一致，是覆盖升级与回退的前提。
