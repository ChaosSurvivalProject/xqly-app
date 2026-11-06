# 星穹旅驿 v2.0.1 · 原生 Windows + 原生 Android

这是**双平台工程基线**版本：在保留已验证的 Android 原生客户端基础上，建立纯 Dart 双平台共享业务核心与原生 Win32 桌面外壳，并把安全、合规、测试与发布链做成可复现的门禁。版本号 `2.0.1`，Android `versionCode=6`，Windows `2.0.1.0`。

> 本版本**不是**「Android 套壳浏览器」的桌面移植：Windows 是 `wWinMain` + `HWND` + 消息循环的原生窗口程序，导航、多标签、托盘、窗口状态、系统集成全部由外壳自己实现；网页内容区可选由系统 WebView2 承载。

---

## 本次更新

### 双平台共享核心（`shared/`，纯 Dart，零第三方依赖）

- 16 个模块统一业务规则：身份与版本策略、可信更新链判定、严格 URL 白名单、版本化桥协议、内容清单校验、星穹玻璃设计令牌、功能开关、全状态 UI、方块合成 2.0 规则、本地全文检索、SHA-256（FIPS 180-4 纯 Dart 实现）、本地优先同步包、冲突合并、可选云端接口形状。
- 设计令牌由 `shared/tool/gen_tokens.dart` 一处真源生成三份产物：`shared/design-tokens.json`、`assets/design-tokens.css`（Android）、`windows/runner/resources/design-tokens.json`（Windows），构建时做字节一致性门禁。
- `shared/pubspec.yaml` 无 `dependencies` / `dev_dependencies`，`dart pub get --offline` 即可构建，**152 个测试全部通过**。

### 原生 Windows 外壳（`windows/`）

- 平台无关 C++17 核心：窗口状态序列化、DPI/多显示器与显示器拔除可见性恢复、多标签与最近关闭恢复、快捷键冲突检测、`xqly://app/` 协议白名单、桥消息校验、设计令牌加载。**在 Linux 上用 CMake 真实编译并跑通 34 个单元测试**。
- Win32 外壳源码：PerMonitorV2 DPI、单实例、托盘与通知、开机启动（默认关闭）、文件关联 `.xqlypack`、自定义协议、触屏/触控笔与等价键鼠输入、三栏大屏布局、关闭行为、WebView2 Runtime 检测。
- 打包配置：MSIX 清单（最小 `internetClient` capability）、AppInstaller、Inno Setup 备选安装器、便携版规范、代码签名脚本（无证书时明确返回 `UNSIGNED`）、冒烟测试。
- 安装版与便携版使用**不同窗口类名、单实例互斥体与数据目录**，避免互相抢焦点或串数据。

### 安全与合规

- 自有界面全面改用「星穹玻璃」设计语言与中性字体栈，清除 Apple / macOS / iOS 等厂商品牌描述与视觉卖点；CI 对 `*.java` / `*.md` / `*.dart` / `*.cpp` 做品牌词扫描。
- 新增 [权限说明](https://github.com/ChaosSurvivalProject/xqly-app/blob/main/docs/PERMISSIONS.md)、[第三方依赖清单](https://github.com/ChaosSurvivalProject/xqly-app/blob/main/docs/THIRD_PARTY.md)、[软件物料清单（SPDX 2.3）](https://github.com/ChaosSurvivalProject/xqly-app/blob/main/docs/dual-platform/SBOM.md)，并更新 [隐私与数据清单](https://github.com/ChaosSurvivalProject/xqly-app/blob/main/docs/PRIVACY.md) 与 [NOTICE.md](https://github.com/ChaosSurvivalProject/xqly-app/blob/main/NOTICE.md) 到双平台口径。
- 发布资产由 `tools/gen_release_assets.py` 从真实文件生成 `SHA256SUMS.txt` 与 `SBOM.spdx.json`，不创建占位假文件。
- GitHub Actions 双平台 CI（`ci.yml`）与 tag 触发的 Release 草稿流水线（`release.yml`），含版本一致性、密钥/凭据扫描、YAML 语法与合规文件检查。

### 保留并继续验证的既有能力

在线/离线双模式、全站离线镜像（228 个随包文件 / 10.04 MB）、本地全文检索、收藏与阅读进度、方块合成 2.0、可信自动更新（发布者钉扎 + 资产名 + 主机白名单 + 整包 SHA-256 + 签名证书钉扎 + 非降级）、平板/横屏/折叠屏自适应、12 步构建门禁。

---

## 下载与校验

| 文件 | 说明 |
|---|---|
| `xqly-app-v2.0.1.apk` | **Android 正式签名 APK**，自动更新链识别的主资产 |
| `xqly-app-v2.0.1-android-universal-release.apk` | 同一 APK 的 ASCII 下载别名（字节相同；用 ASCII 避免 GitHub 资产名归一化） |
| `SHA256SUMS.txt` | 全部资产的 SHA-256 |
| `SBOM.spdx.json` | SPDX 2.3 软件物料清单 |
| `RELEASE_NOTES_2.0.1.md` | 本说明 |

```bash
sha256sum -c SHA256SUMS.txt
apksigner verify --print-certs xqly-app-v2.0.1.apk | grep SHA-256
# 期望证书 SHA-256: e1e8edc56e9700a27ab2cd4ba5f45fa0ba1bd9a88e4a261de7ef3684b3c21c05
```

Android 要求 7.0（API 24）及以上；与 v1.1.0 起所有版本同一签名，**可直接覆盖安装，无需卸载**。

---

## 已知限制（如实标注）

1. **Windows 二进制由 CI 生成，本机不伪造。** 开发沙箱是 Linux，没有 MSVC / Windows SDK / `signtool.exe` / `makeappx.exe`，也没有 Windows 真机与触屏设备。因此本仓库提供的是**完整可审计的 Win32 源码 + 已真实编译测试的平台无关核心**，`xqly.exe` / MSIX / 便携 ZIP 由 `.github/workflows/release.yml` 在 `windows-latest` 上构建。若本次 Release 未列出 Windows 资产，请查看 Actions 运行状态。
2. **代码签名证书缺失时安全降级。** 没有受信任的 Windows 代码签名证书时，签名脚本输出 `SIGN_RESULT=UNSIGNED`，产物必须以 `*-unsigned` / `*-test-signed` 命名并在 Release 标注，不伪装正式签名版。MSIX 清单的 Publisher 当前为测试签名主体，正式发布前需替换为真实证书 Subject。
3. **未做全量 Flutter UI 重写。** 评估结论：本机 `flutter` CLI 卡死（45s 无输出、`flutter create --platforms=windows` 未生成工程），Windows 构建必须在 Windows 主机；为「原生」而重写已稳定且经过真实验证的 Android 原生 Java 链路收益为负。因此采用「纯 Dart 共享业务核心 + 各平台原生外壳」的路线，详见 `docs/dual-platform/AUDIT_2.0.1.md`。
4. **云端同步未配置。** 只定义接口形状，默认关闭，界面显示「未配置」；不虚构服务器地址、账号体系或第三方 AI Key。
5. **真机/性能数据缺失。** 无 `adb` / `emulator` / Windows 真机 / 触屏设备，不提供冷启动时间、帧时间、内存占用等数字，也不用模拟器截图冒充真机证明。
6. **APK 非字节级可复现。** 内容清单幂等，但 `apksigner` v1 签名含时间戳，重复签名哈希可能不同，一律以本 Release 的 `SHA256SUMS.txt` 为准。

完整的已验证 / 未验证清单、测试数字与人工补齐步骤见 `docs/dual-platform/ACCEPTANCE_2.0.1.md`。
