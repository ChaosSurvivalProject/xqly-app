# 隐私与数据清单

本文件说明「星穹旅驿 Android 客户端」v2.0.0 在本机保存什么、向哪里发起网络请求、以及哪些信息**不会**被采集。应用内「设置 → 隐私说明与数据清单」也提供同样的信息。

## 1. 本机数据

| 命名空间 | 存储 | 内容 | 清理缓存时 |
|---|---|---|---|
| `xqly_user` | SharedPreferences（MODE_PRIVATE） | 收藏、收藏夹、自定义标签、阅读历史、书签、阅读进度、最近搜索、阅读设置（字号/行距/正文宽度/主题/显示模式）、游戏存档、功能开关覆盖、更新记录摘要 | **保留** |
| `xqly_cache` | SharedPreferences（MODE_PRIVATE） | 内容包版本 / 文件数 / 字节数、临时下载标记、非敏感缓存键 | 清除 |
| `assets/`（只读，随 APK 内置） | APK 内 | 全站离线镜像 227 文件、搜索索引 13 条、内容清单、离线页面与小游戏 | 不删除 |
| WebView 缓存 | 应用私有目录 | 网页缓存 / DOM storage | 清除 |
| 游戏降级存档 | WebView localStorage | 仅在桥不可用时兜底的游戏状态（普通浏览器打开内置页时） | 随 WebView 缓存清理 |

**用户键统一以 `u.` 开头，缓存键以 `cache.` / `pack.tmp.` / `update.tmp.` 开头**。`PrefsStore.clearAll()` 只允许对 cache 实例调用；`clearCacheSafely()` 不会触碰收藏、历史、进度或游戏存档。

`UserData.migrate()` 提供 schema 0→2 逐级迁移：旧版逗号分隔列表转 JSON 数组、补齐阅读设置默认值；迁移失败**不清空**已有数据。

## 2. 网络请求

| 用途 | 目标 | 触发条件 | 数据 |
|---|---|---|---|
| 在线浏览 | `https://xqly.xt91tv.shop` | 在线模式 | 你主动访问的页面与站点交互（登录、发帖等），与浏览器直连一致 |
| 更新检查 | `https://api.github.com/repos/ChaosSurvivalProject/xqly-app/releases/latest` | 首屏稳定后延迟检查 / 手动检查，受 `auto_update` 开关控制 | 仅 GET Release 元数据，无鉴权、无用户标识 |
| 更新包下载 | `github.com` / `objects.githubusercontent.com` / `release-assets.githubusercontent.com` | 用户确认下载后 | 下载 APK 到应用私有 cache 目录 |
| 外部链接 | 系统浏览器 | 点击非白名单外链 | 由系统浏览器自行处理 |

- **没有任何**第三方统计、广告、崩溃上报、AI 接口、遥测或埋点 SDK。
- **不上传**正文、收藏、历史、搜索词、游戏存档或更新记录。
- 更新 API 固定为官方仓库，不接受页面或消息指定的地址。
- 官方站点禁止明文 `http`；WebView 混合内容一律拒绝；证书错误一律拒绝连接。
- `assets/config/flags.json` 中 `online_enabled=false` 可完全关闭在线直连，`auto_update=false` 可关闭更新检查。

## 3. 日志与调试

- 发布构建 `WebView.setWebContentsDebuggingEnabled(false)`。
- 默认不输出令牌、完整 URL 查询串、账号、正文全文或存档内容。
- 桥层被拒绝的消息只累加计数 `bridgeRejected`，不把内部异常或参数回显给页面。
- 更新中心保存的记录经过 `UpdateCenter.safe()`：URL 替换为 `[链接]`、长文本截断，只保留必要摘要。

## 4. 权限

| 权限 | 用途 |
|---|---|
| `INTERNET` | 在线浏览与更新检查 |
| `ACCESS_NETWORK_STATE` | 判断在线/离线以切换镜像 |
| `WAKE_LOCK` | 视频播放期间保持唤醒 |
| `REQUEST_INSTALL_PACKAGES` | 用户确认后安装已校验的更新包 |

- `XFileProvider` 为 `android:exported="false"`，仅向系统安装器授予单次 URI 权限，且只允许访问应用私有 cache 目录、防目录穿越。
- 组件导出面：`MainActivity` 为唯一导出组件（LAUNCHER），无深层链接、无后台服务、无广播接收器。

## 5. WebView 与桥

- 唯一 JS 接口 `XQLYBridge.postMessage(String)`，版本化消息；每条消息校验版本、来源（与实际发起页面同源）、类型白名单、参数键/类型/长度、权限、幂等 id。
- 危险键（`token` / `file` / `url` / `cmd` / `install` / `exec` / `cookie` / `script`）一律拒绝。
- 桥**不暴露**任意文件读取、安装、命令执行或令牌。
- 默认禁用文件访问、content access、混合内容、表单保存、密码保存。
- 「资料检索」是纯本地精确检索 + 来源定位，**不是 AI**，不联网、不上传任何问题或内容。

## 6. 内容来源与授权

离线镜像来自官网公开页面与公开 API 快照，随 APK 内置，版权归星穹旅驿运营团队所有，不在 MIT 授权范围内（见 [NOTICE.md](../NOTICE.md)）。镜像的论坛公告中可能含服务器路径、备份文件名等运维细节；运营团队可运行 `python3 tools/redact.py --apply` 批量脱敏后重新发布。
