# 星穹旅驿 · 原生 Windows 外壳

原生 **Win32 窗口程序**（`wWinMain` + `HWND` + 消息循环），不是浏览器窗口壳。
网页内容（可选）由 WebView2 承载在原生窗口的内容区；导航、工具栏、多标签、
托盘、窗口状态与系统集成全部由本外壳实现。

平台无关逻辑放在 [`runner/core/`](runner/core/)，**已在本机 Linux 上用 CMake
真实编译并跑过 34 个单元测试**（见下方「已验证 / 未验证」）。

## ⚠ 当前环境限制（必须如实说明）

本仓库的开发沙箱是 Ubuntu 22.04，**没有** MSVC / MSBuild / Windows SDK /
`signtool.exe` / `makeappx.exe`，也没有任何 Windows 真机或触屏设备。
因此：

- `runner/win32/` 下的 Win32 源码是**完整可审计的源码**，但**未在本机编译验证**；
- **本机不产生 `xqly.exe`、MSIX、便携 ZIP 二进制**，也不会伪造它们；
- 这些产物由 [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) 与
  [`release.yml`](../.github/workflows/release.yml) 在 `windows-latest` runner 上构建；
- 没有正式代码签名证书，签名脚本会明确返回 `UNSIGNED`，产物必须标注测试版。

请勿把「Linux 上核心能编译」等同于「Windows 产物已完成」。

## 已验证 / 未验证

| 项目 | 状态 | 证据 |
|---|---|---|
| 平台无关核心（窗口状态/标签/快捷键/协议/桥/令牌） | ✅ 已编译 + 34/34 测试通过 | `cmake -S runner/core -B build && ctest` |
| 设计令牌与 Android 同一份字节 | ✅ 已验证 | `shared/tool/gen_tokens.dart --check` |
| 双平台业务核心（Dart） | ✅ 152/152 测试通过 | `dart test/run_tests.dart ..` |
| Win32 源码编译 | ⚠ 需 Windows 主机 | CI `windows-latest` |
| MSIX / EXE 安装器 / 便携 ZIP | ⚠ 需 Windows 主机 | CI release 流水线 |
| 代码签名 + RFC3161 时间戳 | ⚠ 需正式证书 | `packaging/sign.ps1` |
| 真机 / DPI / 多显示器 / 触屏 / 触控笔 | ❌ 无设备，未测 | 见 `docs/dual-platform/ACCEPTANCE_2.0.1.md` |
| 性能数字（冷启动、帧时间、内存） | ❌ 未测，不提供 | 同上 |

## 环境要求

- 64 位 **Windows 10 1809 (build 17763)** 及以上（明确写在
  `runner/resources/app.manifest` 与设置页，不悄悄改变兼容范围）
- 构建机：**Visual Studio 2022「使用 C++ 的桌面开发」工作负载** +
  Windows 10/11 SDK + CMake 3.15+
- 可选：WebView2 Runtime（系统未安装时设置页给出引导，**不会无提示下载可执行文件**）
- 若使用固定版本 WebView2 Runtime，需在设置页标出所要求的更高系统版本

## 构建

```powershell
# 1) 平台无关核心 + 测试（Linux / Windows 都可跑）
cmake -S windows/runner/core -B build/wincore -DCMAKE_BUILD_TYPE=Release
cmake --build build/wincore -j4
./build/wincore/xqly_core_tests windows/runner/resources/design-tokens.json

# 2) Win32 原生外壳（仅 Windows + MSVC）
cmake -S windows -B windows/build -A x64
cmake --build windows/build --config Release
# 产物：windows/build/Release/xqly.exe
```

## 打包

- MSIX：`packaging/msix/AppxManifest.xml` + `appinstaller.xml`（Publisher 当前为
  **测试签名主体**，正式发布必须替换为受信任证书 Subject）
- EXE 安装器备选：`packaging/installer/xqly.iss`（Inno Setup）
- 便携版：`packaging/portable/PORTABLE.md`
- 签名：`packaging/sign.ps1`（从受限 Secrets 读证书，日志遮蔽，RFC3161 时间戳）
- 冒烟测试：`tests/smoke.ps1`

## 桌面专属体验实现位置

| 能力 | 文件 |
|---|---|
| 记住窗口尺寸/位置/最大化/分屏、DPI 100–300%、显示器拔除 | `win32/window.cpp`、`core/platform_core.cpp` |
| 单实例（再启动激活已有窗口） | `win32/system_integration.cpp` |
| 关闭只关窗口 / 托盘菜单「退出」 | `win32/window.cpp`、`win32/input.cpp` |
| 多标签、拖拽、恢复最近关闭 | `core/platform_core.cpp` |
| Ctrl+T/W/Tab/F/K/, F5/Ctrl+R/Esc 快捷键 + 冲突提示 | `core/platform_core.cpp`、`win32/input.cpp` |
| 托盘、通知、开机启动（默认关闭）、文件关联、自定义协议 | `win32/system_integration.cpp` |
| 触屏 / 触控笔 / 游戏键鼠映射 | `win32/input.cpp` |
| 三栏大屏布局、正文最大宽度 | `win32/window.cpp` (`RelayoutContent`) |
| 页面不可见时暂停媒体/动画/游戏帧循环 | `win32/window.cpp` (`WM_ACTIVATE`) |
| 版本化桥校验、白名单协议 | `core/security_core.cpp` |

## 与 Android 的关系

业务规则的唯一真源是 [`../shared/`](../shared/) 的纯 Dart 包；Android 继续使用
已验证的零 Gradle 原生 Java + WebView 链，Windows 通过版本化平台通道消费同一份规则。
**没有为「原生」分别写两套业务代码**，也没有把 Android 底部 Dock 原样搬到桌面。

详细的迁移映射、未完成项与人工补齐步骤见 [`docs/dual-platform/ACCEPTANCE_2.0.1.md`](../docs/dual-platform/ACCEPTANCE_2.0.1.md)。
