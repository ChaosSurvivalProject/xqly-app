# 隐私与数据清单

适用版本 **v2.0.1**（Android `versionCode=6` / Windows `2.0.1.0`）。

本文件说明「星穹旅驿」双平台客户端在本机保存什么、向哪里发起网络请求、以及哪些信息**不会**被采集。应用内「设置 → 隐私说明与数据清单」提供同样的信息。权限与系统集成的完整逐项说明见 [PERMISSIONS.md](PERMISSIONS.md)。

---

## 1. 本机数据

### Android

| 命名空间 | 存储 | 内容 | 清理缓存时 |
|---|---|---|---|
| `xqly_user` | SharedPreferences（`MODE_PRIVATE`） | 收藏、收藏夹、自定义标签、阅读历史、书签、阅读进度、最近搜索、阅读设置（字号/行距/正文宽度/主题/显示模式）、游戏存档、功能开关覆盖、更新记录摘要 | **保留** |
| `xqly_cache` | SharedPreferences（`MODE_PRIVATE`） | 内容包版本 / 文件数 / 字节数、临时下载标记、非敏感缓存键 | 清除 |
| `assets/`（只读，随 APK 内置） | APK 内 | 全站离线镜像、搜索索引 13 条、内容清单、离线页面与小游戏 | 不删除 |
| WebView 缓存 | 应用私有目录 | 网页缓存 / DOM storage | 清除 |
| 游戏降级存档 | WebView localStorage | 仅在桥不可用时兜底的游戏状态（普通浏览器打开内置页时） | 随 WebView 缓存清理 |

**用户键统一以 `u.` 开头，缓存键以 `cache.` / `pack.tmp.` / `update.tmp.` 开头**。`PrefsStore.clearAll()` 只允许对 cache 实例调用；`clearCacheSafely()` 不会触碰收藏、历史、进度或游戏存档。`UserData.migrate()` 提供 schema 0→2 逐级迁移，迁移失败**不清空**已有数据。

### Windows

| 位置 | 内容 | 说明 |
|---|---|---|
| 安装版 `%LOCALAPPDATA%\XQLY` / 便携版 `<EXE 目录>\Data` | 窗口状态（位置/尺寸/最大化/分屏）、标签会话、用户偏好、同步包记录 | 卸载不静默删除用户数据 |
| 应用私有数据 | 收藏、阅读进度、游戏存档、设置（与 Android 语义一致，由共享核心约束字段） | 两种安装形态数据目录互相隔离 |
| `data/design-tokens.json` | 只读设计令牌 | 与 Android `assets/design-tokens.css` 由同一份 Dart 真源生成 |

目录只读时明确提示并降级到用户数据目录，不静默失败。

### 随包内容

`assets/MANIFEST.json` 记录 **228 个文件 / 10,531,971 bytes（10.04 MB）** 的 SHA-256 与体积，含双平台共享的 `design-tokens.css`；启动时抽样校验，防路径穿越、超大解压比、截断与版本回退。

---

## 2. 网络请求

| 用途 | 目标 | 触发条件 | 数据 |
|---|---|---|---|
| 在线浏览 | `https://xqly.xt91tv.shop` | 在线模式（两个平台一致） | 你主动访问的页面与站点交互（登录、发帖等），与浏览器直连一致 |
| 更新检查 | `https://api.github.com/repos/ChaosSurvivalProject/xqly-app/releases/latest` | 首屏稳定后延迟检查 / 手动检查，受 `auto_update` 开关控制 | 仅 GET Release 元数据，无鉴权、无用户标识 |
| 更新包下载 | `github.com` / `objects.githubusercontent.com` / `release-assets.githubusercontent.com` | 用户确认下载后 | 下载 APK 到应用私有 cache 目录 |
| 外部链接 | 系统浏览器 / 系统默认处理器 | 点击非白名单外链 | 由系统自行处理 |
| 云端同步 | **无默认地址** | 默认关闭且「未配置」 | 不发起任何请求 |

- **没有任何**第三方统计、广告、崩溃上报、AI 接口、遥测或埋点 SDK。
- **不上传**正文、收藏、历史、搜索词、游戏存档或更新记录。
- 更新 API 固定为官方仓库，不接受页面或消息指定的地址。
- 官方站点禁止明文 `http`；WebView / WebView2 混合内容一律拒绝；证书错误一律拒绝连接。
- `assets/config/flags.json` 中 `online_enabled=false` 可完全关闭在线直连，`auto_update=false` 可关闭更新检查。

### 跨设备同步（本地优先）

同步默认走**用户主动导出/导入的本地同步包**（`.xqlypack`），不依赖云端：

- 包内只含收藏/标签/阅读进度/设置白名单项/游戏存档元数据/删除墓碑，以及 schema、设备 ID、Lamport 逻辑时钟、创建时间与校验和。
- **拒绝**缓存正文、离线镜像、窗口状态、凭据、Token 与危险键；截断、篡改、重复 id、非法 schema 一律整体拒绝。
- 本地传输要求加密 + 设备双向信任 + 用户逐次确认，配对凭据 120 秒过期。
- 合并时标签取并集；阅读进度同时钟冲突**交用户确认**，不静默按设备 ID 定序；游戏存档不盲目覆盖；设置默认不覆盖。
- 云端接口只定义形状（`/v1/sync/changes`、`/v1/sync/items/{id}`、`/v1/sync/session`），**没有内置服务器、账号体系或凭据**，界面显示「未配置」。

---

## 3. 日志与调试

- Android 发布构建 `WebView.setWebContentsDebuggingEnabled(false)`。
- 默认不输出令牌、完整 URL 查询串、账号、正文全文或存档内容。
- 桥层被拒绝的消息只累加计数 `bridgeRejected`，不把内部异常或参数回显给页面。
- 更新中心保存的记录经过 `UpdateCenter.safe()`：URL 替换为 `[链接]`、长文本截断，只保留必要摘要。
- 签名脚本从受限 Secrets 读取证书，日志遮蔽口令；Windows 无正式证书时明确输出 `SIGN_RESULT=UNSIGNED`，不伪造签名状态。

---

## 4. 权限（摘要）

| 平台 | 权限 / 能力 | 用途 |
|---|---|---|
| Android | `INTERNET` | 在线浏览与更新检查 |
| Android | `ACCESS_NETWORK_STATE` | 判断在线/离线以切换镜像 |
| Android | `WAKE_LOCK` | 视频播放期间保持唤醒 |
| Android | `REQUEST_INSTALL_PACKAGES` | 用户确认后安装已校验的更新包 |
| Windows | MSIX `internetClient` | 唯一声明的 capability |
| Windows | 开机启动 / 托盘 / 通知 | **默认关闭**，仅用户明确同意后启用 |
| Windows | 文件关联 `.xqlypack`、协议 `xqly://app/` | 安装时注册，命令走白名单校验 |

`XFileProvider` 为 `android:exported="false"`，仅向系统安装器授予单次 URI 权限。Android 唯一导出组件是 `MainActivity`（LAUNCHER），无深层链接、无后台服务、无广播接收器。Windows 不请求管理员权限（`asInvoker`）、不写 `HKLM`、不装驱动、不注入全局钩子。

> 完整逐项说明、默认状态与已知配置风险见 [PERMISSIONS.md](PERMISSIONS.md)。

---

## 5. WebView 与桥（双平台一致）

- 唯一 JS 接口 `XQLYBridge.postMessage(String)`（Android）/ 等价平台通道（Windows），版本化消息；每条消息校验版本、来源（与实际发起页面同源）、类型白名单、参数键/类型/长度、权限、幂等 id。
- 危险键（`token` / `file` / `url` / `cmd` / `install` / `exec` / `cookie` / `script`）一律拒绝。
- 桥**不暴露**任意文件读取、安装、命令执行或令牌。
- 默认禁用文件访问、content access、混合内容、表单保存、密码保存。
- 「资料检索」是纯本地精确检索 + 来源定位，**不是 AI**，不联网、不上传任何问题或内容。

---

## 6. 内容来源与授权

离线镜像来自官网公开页面与公开 API 快照，随 APK 内置，版权归星穹旅驿运营团队所有，**不在 MIT 授权范围内**（见 [NOTICE.md](../NOTICE.md)）。镜像的论坛公告中可能含服务器路径、备份文件名等运维细节；运营团队可运行 `python3 tools/redact.py --apply` 批量脱敏后重新发布。

镜像中残留的 `-apple-system` / `AppleWebKit` 等字符串是第三方站点原始代码与标准 UA 的**技术标识**，不是本项目自有界面的品牌描述；详见 [第三方依赖清单](THIRD_PARTY.md) 第 4 节。
