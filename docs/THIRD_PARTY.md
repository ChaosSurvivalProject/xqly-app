# 第三方依赖与许可证清单

适用版本 **v2.0.1**。结论先行：**客户端运行时不链接、不内置、不分发任何第三方库**。所有第三方软件只出现在「构建期工具」「系统自带运行时组件」与「官网离线镜像内容」三类，逐项列在下面。

---

## 1. 运行时依赖（随客户端一起运行的库）

| 平台 | 运行时依赖 | 说明 |
|---|---|---|
| Android | 无 | 纯 Android Framework + 原生 Java + 系统 WebView；`assets/` 内的 JS/CSS 均为本项目自研或官网原始镜像，无 npm/bower/CDN 依赖 |
| Windows | 无（WebView2 为可选系统组件） | 纯 Win32 API + 标准 C++17 静态库 `xqly_platform_core`；不使用 .NET、Electron、CEF、Qt 或任何 DLL 依赖 |
| 共享核心 | 无 | `shared/pubspec.yaml` 的 `dependencies` / `dev_dependencies` 均为空，`dart pub get --offline` 即可构建；SHA-256 为本项目按 FIPS 180-4 纯 Dart 实现 |

**明确没有**：广告 SDK、统计/埋点 SDK、崩溃上报、推送 SDK、AI/大模型 API、遥测、第三方登录、支付、地图、社交分享、动态下发的远程代码、自动升级用的第三方框架。

---

## 2. 构建期工具（不随客户端分发）

版本以 `.github/workflows/ci.yml` 的 `env` 锁定为准；`build.sh` 不访问网络。

| 组件 | 版本 | 用途 | 许可证 | 随包分发 |
|---|---|---|---|---|
| Android SDK build-tools | 35.0.0 | `aapt2` / `d8` / `zipalign` / `apksigner` | Android SDK License | 否 |
| Android platform | android-35 | `android.jar` 编译目标 | Android SDK License | 否 |
| OpenJDK / javac | 17（Temurin） | 编译 Java 源码、跑 JVM 测试 | GPL-2.0-only WITH Classpath-exception-2.0 | 否 |
| Dart SDK | 2.17.0 | 共享核心静态分析、测试、设计令牌生成 | BSD-3-Clause | 否 |
| Node.js | 22 | 游戏引擎 JS 测试 | MIT | 否 |
| Python | 3.11 | 搜索索引 / 内容清单 / 图标 / 发布资产生成 | PSF-2.0 | 否 |
| CMake | ≥ 3.15 | Windows 平台无关核心编译与 `ctest` | BSD-3-Clause | 否 |
| Visual Studio 2022 MSVC | 17.x | Win32 外壳编译 | Microsoft Visual Studio License | 否 |
| Windows SDK | 10.x | `makeappx.exe` / `signtool.exe` | Microsoft Windows SDK License | 否 |
| Inno Setup | 6.x（备选安装器） | MSIX 不兼容时生成签名 EXE 安装器 | Inno Setup License | 否 |
| Pillow | 预装 | 生成自绘品牌图标与 MSIX 图片资源 | HPND | 否 |
| PyYAML | CI 临时安装 | 校验工作流 YAML 语法 | MIT | 否 |

---

## 3. 系统运行时组件（不由本项目分发）

| 组件 | 平台 | 说明 |
|---|---|---|
| Android System WebView | Android | 系统组件，随系统/OEM 更新；本项目不内置、不指定版本、不静默安装 |
| WebView2 Runtime | Windows | **可选**，系统未安装时设置页给出引导；本项目不打包、不无提示下载 |
| 系统中文字体 | 双平台 | 只使用 `system-ui` / `Segoe UI` / `Roboto` / `Noto Sans CJK SC` / `Microsoft YaHei` 字体回退栈，**不随包分发字体文件** |

---

## 4. 随包内容与权利划分

| 内容 | 来源 | 权利与授权 |
|---|---|---|
| 客户端自研代码（`src/`、`shared/`、`windows/`、`tools/`、`build.sh`、`scripts/`） | 本项目 | MIT，见 [LICENSE](../LICENSE) |
| 自绘图标（`res/`、`windows/runner/resources/xqly.ico`、`windows/packaging/msix/Assets/*.png`） | 本项目用 Pillow / 代码绘制 | MIT，不使用任何厂商图标或商标 |
| 离线小游戏、内置页面、桥与规则引擎 | 本项目 | MIT |
| `assets/site/`、`assets/data/`、`assets/archive.html`、`docs/全站内容档案.html` | 官网 `https://xqly.xt91tv.shop` 公开页面与公开 API 快照 | **版权归星穹旅驿运营团队，不在 MIT 授权范围内**，见 [NOTICE.md](../NOTICE.md) |

### 关于镜像中残留的厂商技术标识

官网原始静态资源（`assets/site/**`）保持抓取时的字节内容，以保证离线渲染与在线一致，因此其中仍可检索到：

- CSS 字体回退栈 `-apple-system` / `BlinkMacSystemFont`；
- 构建产物注释或特性检测中的 `AppleWebKit` 等字符串；
- `tools/mirror.py` 用于复现爬取的浏览器 User-Agent 中的 `AppleWebKit`。

这些是**第三方站点原始代码与标准 UA 的技术标识**，不是本项目自有界面的品牌描述或视觉卖点，也不代表任何厂商背书。本项目自有界面（Java/Dart/C++/自研 HTML/CSS）已全部改用「星穹玻璃」自有设计语言与中性字体栈，并在 CI 中对 `*.java` / `*.md` / `*.dart` / `*.cpp` 做品牌模仿表述扫描。

> 用户在本阶段明确要求「不再使用 Apple、macOS、iOS 等品牌词描述自有界面」。镜像属于**内容**而非自有界面代码，改动它会破坏离线镜像完整性与内容清单哈希，因此保留原样并在此如实说明；如运营团队希望连镜像一起清理，需重新跑 `tools/mirror.py` 并重建 `assets/MANIFEST.json` 与 APK。

---

## 5. 依赖与升级策略

- **锁定**：Dart SDK 2.17.0、Android build-tools 35.0.0、android-35、JDK 17、Node 22 全部在 CI 中显式锁定，不批量升级。
- **离线**：`shared/` 零依赖，`dart pub get --offline` 可构建；Android 构建链零 Gradle、零网络。
- **无动态下发**：运行时不下载代码、不加载远程脚本、不执行页面注入的 JS 接口之外的能力。
- **供应链校验**：随包 `assets/MANIFEST.json` 记录 228 个文件的 SHA-256 与体积；更新链固定发布者、资产名、下载主机白名单、整包 SHA-256 与 APK 签名证书钉扎；Release 资产另附 `SHA256SUMS.txt` 与 SPDX 软件物料清单。

机器可读的软件物料清单见 [SBOM.md](dual-platform/SBOM.md)，生成器为 `tools/gen_release_assets.py`（SPDX 2.3）。
