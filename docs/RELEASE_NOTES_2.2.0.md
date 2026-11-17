# 星穹旅驿 v2.2.0 · 开发者平台模组与统一 UI 契约

Android `versionCode=9` / `versionName=2.2.0`，Windows `2.2.0.0`。
本次是**功能性新增**（应用类模组 + 统一 UI 契约扩展），不是 UI bug 修复版。

---

## 1. 新增：dev-portal 应用类模组

按「应用抽屉 → 应用首页 → 详情 → 模态」四层导航接入，作为与资料库 / 游戏同级的
**单一 WebView 池应用路由**，不是新 Activity、不是第二个容器：

- 底部 Dock 新增「开发」项 + 侧边药丸「⌘ 开发」双入口。
- 纳入 `OverlayController` 互斥：返回键、`✦ 首页` 逃生、页面关闭按钮全部收敛，
  延续 v2.1.1 的「任意时刻最多一个浮层可见」不变量。
- 页面为纯本地资源（`assets/apps/dev-portal/` 共 4 个文件）：连接管理、应用抽屉、
  流式会话占位、只读工作区、工具审批、接入条件与隐私、16 条方法的权限矩阵。
- 离线只读外壳：明确写出「现在没有内容 / 为什么 / 下一步做什么」，不做无限转圈。

**回退决策如实记录**：会话内无法取得上游许可证链、可复现构建与体积证据，
因此放弃内置执行引擎，落地纯自研「受控远程门户 v1」。不引入 Node / 模型 / AGPL /
API Key / 预置服务器，不加载第三方插件，不伪造连接成功。详见
[DEV_PORTAL_MODULE.md](DEV_PORTAL_MODULE.md) 第 1 节。

## 2. 新增：xqly-bridge/1 桥协议与端点策略

- 16 条方法的 schema / 权限 / 超时 / 幂等 / 审批 / 网络约束；
  外层 `BridgeProtocol` + 内层 `BridgeV1` 双层校验。
- 默认权限仅 6 项，`credential` / `network:devportal` / `user_approval` /
  `file:export` / `telemetry:optional` / `diagnostics` 一律不授予 → 越权 100% 拒绝。
- 端点仅 WSS/SSE/HTTPS、端口 443/8443、公网域名、用户 allowlist、可选证书 pin；
  私网 / 环回 / 链路本地 / 明文 / 深链穿越全部拒绝。
- 日志与响应统一脱敏（`redact` / `responseSafe`），`envInfo` 不返回设备标识与路径。
- `xqly.devportal.connect` / `send` 在未配置端点时如实拒绝；
  `approveTool` 在 v1 一律拒绝（不提供工具执行能力）。

## 3. 新增：统一 UI 单源 token 扩展（契约 v2）

- `DesignTokens.designContract()` 定义四层 token、组件状态、断点、动效、对比度、
  反馈位置与三平台映射；`gen_tokens.dart` 一次生成 5 份字节一致的产物，
  `--check` 接入 `build.sh` [1/12] 门禁。
- 前端 `app.css` 全部使用 `var(--xq-*)`，零硬编码色值，含焦点态与减弱动效适配。
- 详见 [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md)。

## 4. 本次修复的真实缺陷

| 问题 | 修复 |
|---|---|
| `EndpointPolicy.normalizePath` 把 `xqly://` 折叠成 `xqly:/`，所有 Deep Link 被误拒 | 只归一化 scheme 之后的路径，Dart / Java 双侧同步 + 回归用例 |
| 端点校验里 `BAD_HOST` 抢先吞掉私网拒绝原因，`PRIVATE_NETWORK` 分支不可达 | 调整判定顺序，拒绝原因准确 |
| `approveTool` 把 `scope` 当成 `grant` 校验，字段语义混用 | scope 校验为合法作用域，grant 校验 `once/session/deny` |
| Dart `BridgeV1.redact` 的 raw 单引号字符串被 `\'` 提前终止、`$1` 被当作字符串插值 | 改用 `\x27` 与 `replaceAllMapped` |
| `MainActivity` 自定义了一份与 Dart/Java 不一致的默认权限集合 | 直接复用 `BridgeV1.DEFAULT_PERMISSIONS`，三端同一真源 |

## 5. 版本真源（逐字校验）

| 位置 | 值 |
|---|---|
| `AndroidManifest.xml` | `versionCode=9` / `versionName=2.2.0` |
| `MainActivity.java` | `VERSION_NAME=2.2.0` / `VERSION_CODE=9` |
| `build.sh` | `V_CODE=9` / `V_NAME=2.2.0` |
| `shared/lib/src/identity.dart` | `versionName=2.2.0` / `versionCode=9` / `windowsVersion=2.2.0.0` |
| `windows/runner/resources/resource.h` | `2,2,0,0` / `"2.2.0.0"` |
| `windows/packaging/msix/AppxManifest.xml` | `Version="2.2.0.0"` |

## 6. 测试与门禁

| 层 | 用例 | 结果 |
|---|---|---|
| Dart 共享核心 | 178 | 全绿 |
| JVM core | 136 | 全绿 |
| Node 游戏引擎 | 19 | 全绿 |
| Node dev-portal 前端桥 | 15 | 全绿 |
| C++ 平台核心（Linux 真实编译） | 34 | 全绿 |
| **合计** | **382** | **全部通过** |

`build.sh` 12 步门禁：版本一致性 → `dart analyze` + 令牌生成/校验 → 搜索索引与
内容清单生成/校验 → 四套测试 → 资源编译 → Java 编译 → DEX → APK 组装对齐 →
APK 内 `MANIFEST.json` / `design-tokens.css` 字节比对 → 签名与 `apksigner verify`。

随包资源清单：**232 个文件 / 10,560,308 bytes（10.07 MB）**，含 4 个 dev-portal 资源。

## 7. 发布资产

| 资产 | 说明 |
|---|---|
| `xqly-app-v2.2.0.apk` | OTA 主资产，正式发布证书签名（`e1e8edc5…c21c05`） |
| `xqly-app-v2.2.0-android-universal-release.apk` | 附加下载项（ASCII 名，避免上传后前缀丢失） |
| `xqly-app-v2.2.0-windows-x64-setup.msix` | Windows 安装版，由 `windows-latest` CI 产出，**测试签名** |
| `xqly-app-v2.2.0-windows-x64-portable.zip` | Windows 便携版，同上 |
| `RELEASE_NOTES_2.2.0.md` / `SBOM.spdx.json` / `SHA256SUMS.txt` | 说明、软件物料清单与校验和 |

## 8. 已知边界（如实标注）

- 游戏（方块合成）与离线档案（archive）仍由本机 WebView 承载，未做与资料库同级的原生组件重写。
- 主站 Vue SPA 内的弹层由页面自身管理，Android 侧提供返回键 / 首页重载兜底。
- dev-portal v1 **不提供**真实远程会话、工具执行、模型推理与凭据解锁；
  端点白名单默认空，界面显示「未配置」而非伪造连接。
- Windows 产物为 CI 测试签名，未配置组织代码签名证书，不伪装正式版。
- 开发沙箱为 Linux，本机不产生 Windows 二进制；Win32 源码完整可审计，平台无关核心已编译测试。
