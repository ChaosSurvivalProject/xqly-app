# 权限与系统集成说明

适用版本 **v2.0.1**（Android `versionCode=6` / Windows `2.0.1.0`）。

本文逐项列出两个平台申请的能力、用途、默认状态、可否关闭，以及关闭后的影响。设计原则是**默认最小权限、默认关闭可选项、任何写操作都需要用户显式动作**。权限的实际代码位置见文末索引。

---

## 1. Android 权限

`AndroidManifest.xml` 共申请 4 项权限，**没有**定位、通讯录、相机、麦克风、短信、电话、外部存储读写、后台自启动、悬浮窗、无障碍服务或查询应用列表权限。

| 权限 | 用途 | 触发时机 | 可否关闭 | 拒绝后的影响 |
|---|---|---|---|---|
| `INTERNET` | 在线模式直连官网、检查 GitHub Release 元数据、用户确认后下载更新包 | 在线模式 / 更新检查 | 可用 `assets/config/flags.json` 的 `online_enabled=false` 完全关闭在线直连；`auto_update=false` 关闭更新检查 | 离线模式、阅读、搜索、收藏、游戏全部可用；仅失去在线写操作与自动更新 |
| `ACCESS_NETWORK_STATE` | 判断在线/离线以决定是否降级到内置镜像 | 启动时与网络状态变化 | 系统级只读查询，无需用户授权 | 无法自动切换模式，需手动切换 |
| `WAKE_LOCK` | 播放站内视频期间保持屏幕唤醒 | 仅视频全屏播放 | 不可单独关闭（随播放结束自动释放） | 长时间播放可能息屏 |
| `REQUEST_INSTALL_PACKAGES` | 用户确认后，把**已通过完整校验**的更新 APK 交给系统安装器 | 仅用户点击「安装更新」 | 可完全不使用（不更新即可） | 无法在应用内完成安装；仍可手动下载 APK 安装 |

### 组件与数据边界

| 项 | 配置 | 说明 |
|---|---|---|
| `MainActivity` | `exported="true"`（LAUNCHER） | 唯一导出组件；无深层链接、无后台服务、无广播接收器、无 ContentProvider 对外暴露 |
| `XFileProvider` | `exported="false"`，`grantUriPermissions="true"` | 只向系统安装器按次授予 URI，且仅允许应用私有 cache 目录，做了目录穿越防护 |
| WebView | 默认禁用文件访问、content 访问、调试、混合内容、表单保存、密码保存 | 发布构建 `setWebContentsDebuggingEnabled(false)` |
| JS 桥 | 唯一接口 `XQLYBridge.postMessage(String)` | 版本化消息；校验版本、来源（绑定实际发起页面）、类型白名单、参数键/类型/长度、权限、幂等 id |
| 本地存储 | `SharedPreferences` `MODE_PRIVATE` | 用户数据 `xqly_user` 与缓存 `xqly_cache` 分文件；清理缓存不动收藏/历史/进度/游戏存档 |

### 已知配置风险（如实标注，不伪称已修复）

| 配置 | 现状 | 影响与建议 |
|---|---|---|
| `android:usesCleartextTraffic="true"` | 历史遗留配置 | 代码层 `UrlPolicy` 已拒绝明文 `http` 官方站点、混合内容与证书错误，实际不会走明文通道；但 manifest 层面仍放开。建议下一版评估改为 `false` 并回归离线镜像回归测试 |
| `android:allowBackup="true"` | 允许系统备份/恢复应用私有数据 | 用户数据不含账号口令、Token 或第三方凭据；同步包含设备 ID 与 Lamport 时钟。若需更严格，可改 `false` 或补充 `dataExtractionRules` / `fullBackupContent` |

---

## 2. Windows 能力与系统集成

Windows 外壳是原生 Win32 程序（`wWinMain` + `HWND` + 消息循环），不是浏览器窗口壳。MSIX 清单只声明 `internetClient` 一个 capability，**没有** `broadFileSystemAccess`、`runFullTrust` 之外的受限能力、位置、摄像头、麦克风。

| 能力 | 实现位置 | 默认状态 | 用户如何关闭 | 关闭后影响 |
|---|---|---|---|---|
| MSIX `internetClient` | `packaging/msix/AppxManifest.xml` | 随安装启用（客户端需要联网） | 拔网线 / 防火墙阻止 / 应用内关闭在线模式 | 离线阅读、检索、游戏仍可用 |
| 开机启动 | `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`（`SetAutoStart`） | **默认关闭** | 设置页开关；或删除该注册表值 | 仅不自动启动，无功能损失 |
| 托盘图标 + 通知 | `system_integration.cpp` | 最小化到托盘默认关闭 | 设置页关闭「最小化到托盘」 | 关闭窗口即退出应用 |
| 单实例 | 命名互斥体 + 激活已有窗口 | 始终启用 | 不可关闭（保证数据目录不被两个进程同时写） | — |
| 自定义协议 `xqly://app/...` | `HKCU\Software\Classes\xqly` | 安装时注册 | 卸载或删除该注册表项 | 无法通过链接唤起；`ParseProtocolUri` 只接受 `open/search/sync/update/settings` 白名单命令 |
| 文件关联 `.xqlypack` | `HKCU\Software\Classes\.xqlypack` | 安装时注册 | 卸载或删除该注册表项 | 无法双击打开同步包；打开前仍校验扩展名、体积，严格解析由共享核心完成 |
| 窗口状态持久化 | `Data\window-state.json`（或安装版 LocalAppData 对应文件） | 始终启用 | 删除该文件 | 窗口位置/尺寸/最大化状态回到默认 |
| DPI / 多显示器 | `SetProcessDpiAwarenessContext(PER_MONITOR_AWARE_V2)` | 始终启用 | 系统设置 | 显示器拔除后窗口会自动移回可见区域 |
| 触屏 / 触控笔 | `RegisterTouchWindow` + `WM_TOUCH` / Pointer API | 设备支持时启用 | 系统设置 | 屏幕按钮、键盘、滑动提供等价输入，不依赖触屏 |
| WebView2 Runtime | 系统组件，**可选** | 未安装时设置页给出引导 | 可不安装 | 网页内容区不可用；原生窗口、导航、设置、游戏框架仍可用。**不会无提示下载可执行文件** |

### 数据目录与隔离

| 形态 | 数据目录 | 应用标识 |
|---|---|---|
| 安装版 | `%LOCALAPPDATA%\XQLY`（`FOLDERID_LocalAppData`） | 窗口类 `XQLY_Win32_Window` + 对应互斥体 |
| 便携版（EXE 旁有 `portable.flag` 或 `Data\`） | `<EXE 目录>\Data` | 窗口类 `XQLY_Win32_Window_Portable` + 不同互斥体 |

两种形态使用**不同的窗口类名、单实例互斥体与数据目录**，避免同时安装时互相抢焦点或串数据。EXE 所在目录只读时会明确提示并降级到用户数据目录，不会静默失败。卸载不会静默删除用户数据（见 `packaging/installer/xqly.iss`）。

### Windows 明确不做的事

- 不请求管理员权限（`requestedExecutionLevel = asInvoker`）。
- 不写 `HKLM`、不装驱动、不注入全局钩子、不读取其他应用数据。
- 不静默下载或安装 WebView2 Runtime、不静默自更新可执行程序。
- 不使用受限 capability 规避沙箱设计。

---

## 3. 跨设备同步相关的权限边界

| 项 | 行为 |
|---|---|
| 云端同步 | **默认关闭且「未配置」**。代码只定义 `/v1/sync/changes`、`/v1/sync/items/{id}`、`/v1/sync/session` 接口形状，没有内置服务器地址、账号体系或凭据 |
| 本地传输 | 必须同时满足：加密、设备双向信任、用户逐次确认；配对凭据 120 秒过期 |
| 同步包内容 | 只含收藏/标签/阅读进度/设置白名单项/游戏存档元数据/墓碑，**拒绝**缓存正文、镜像、窗口状态、凭据与危险键；截断或篡改包因校验和不匹配被整体拒绝 |
| 冲突处理 | 标签取并集；阅读进度同时钟冲突交用户确认，不静默按设备 ID 定序；游戏存档不盲目覆盖；设置默认不覆盖 |

---

## 4. 权限与代码索引

| 平台 | 文件 |
|---|---|
| Android 权限声明 | `AndroidManifest.xml` |
| Android URL / 桥白名单 | `src/cn/xqly/app/core/UrlPolicy.java`、`core/BridgeProtocol.java`、`shared/lib/src/url_policy.dart`、`shared/lib/src/bridge_protocol.dart` |
| Android 安装与文件授予 | `src/cn/xqly/app/XFileProvider.java`、`Updater.java` |
| Windows MSIX capability | `windows/packaging/msix/AppxManifest.xml` |
| Windows 注册表与单实例 | `windows/runner/win32/system_integration.cpp` |
| Windows 数据目录 | `windows/runner/win32/main.cpp`（`ResolveDataDir`） |
| Windows 协议/桥校验 | `windows/runner/core/security_core.cpp` |
| 功能开关默认值 | `shared/lib/src/feature_flags.dart`（默认关闭项不允许用户覆盖为开） |

> 隐私数据的具体内容见 [隐私与数据清单](PRIVACY.md)；第三方组件与许可证见 [第三方依赖清单](THIRD_PARTY.md)。
