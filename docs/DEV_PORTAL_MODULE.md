# 开发者平台模组（dev-portal v1）· 设计与回退决策

适用版本 **v2.2.0**。这是一个**应用类模组**：以「单一 WebView 池 + 应用路由 + 独立沙箱」接入，
不是新 Activity、不是第二个容器、不内置执行引擎。

---

## 1. 选型判定与回退（如实记录）

提示文原本期望一个「类开发者工作台」的模组，含连接管理、流式会话、工作区与工具审批。
阶段 0 复核后触发**回退**：会话内无法取得上游项目的许可证链、可复现构建与体积证据，
因此**放弃内置执行引擎**，落地纯自研的「受控远程门户 v1」：

| 未采用 | 原因 |
|---|---|
| 内置 Node / 模型 / 执行引擎 | 无上游许可证链与可复现构建证据，体积与供应链不可控 |
| AGPL / 第三方运行时 | 与 MIT 客户端授权边界冲突，且需分发额外二进制 |
| 预置远程服务器 / API Key / 凭据 | 无用户授权的服务端，伪造连接或凭据属于虚假能力 |
| 动态下发远程代码 | 违反「不加载未随包审计的代码」红线 |

**v1 明确不提供**：远程会话真实执行、工具执行、模型推理、文件读写、命令执行、凭据解锁。
页面如实显示「未配置 / 未连接 / 不可用」，不用本地假数据伪装成功。

---

## 2. 架构

```
MainActivity
  └─ OverlayController（唯一真源，最多 1 个浮层可见）
       ├─ ARCHIVE / LIBRARY / GAME
       └─ DEV_PORTAL  ← 新增，同等级应用路由
            └─ devPortalWeb（单一 WebView 池里的一个实例，延迟首次加载）
                 └─ file:///android_asset/apps/dev-portal/index.html
                      ├─ design-tokens.css（共享单源）
                      ├─ app.css / app.js
                      └─ bridge-v1.js（独立沙箱，不复用主站 bridge-shim.js）
```

- **入口**：底部 Dock 新增「开发」项（`__DEV_PORTAL__`）+ 侧边药丸「⌘ 开发」。
- **关闭**：返回键 `onBack()`、首页逃生 `resetToHome()`、页面 `XQApp.close()` → 外层 `closeOverlay`
  → `closeAllOverlays()`，全部收敛到同一个 `OverlayController`，杜绝 v2.1.0 那种「叠加且关不掉」。
- **注册表**：`AppRegistry`（Java / Dart 两份镜像）登记 3 个应用，dev-portal 为 `READ_ONLY`，
  独立 `storagePartition`，导航深度上限 4。
- **URL 策略**：`apps/dev-portal/index.html` 用**完整相对路径精确匹配**放行，
  `apps/<任意其它子目录>/...` 一律 BLOCKED；同目录仅 4 个资源在白名单内。

---

## 3. xqly-bridge/1 双层校验

页面 → 外层 `BridgeProtocol`（`type=devportal`，只允许一个 `envelope` 字符串）
→ 内层 `BridgeV1.validate`（16 方法 / 权限 / 幂等 / 限流 / 参数 / 离线 / 遥测 / 审批）
→ 宿主 `onDevPortal()` 更新状态机 → `portalReply()` **只回投到当前可见的 dev-portal WebView**。

| # | 方法 | 权限 | 超时 | 幂等 | 需审批 | 网络 |
|---|---|---|---|---|---|---|
| 1 | `xqly.env.getInfo` | public | 1s | 是 | 否 | 否 |
| 2 | `xqly.ui.setTitle` | ui | 1s | 否 | 否 | 否 |
| 3 | `xqly.ui.showFeedback` | ui | 1s | 否 | 否 | 否 |
| 4 | `xqly.theme.subscribe` | public | 1s | 是 | 否 | 否 |
| 5 | `xqly.nav.open` | nav | 2s | 否 | 否 | 否 |
| 6 | `xqly.link.open` | link | 2s | 否 | 否 | 否 |
| 7 | `xqly.credential.getEndpoint` | credential | 3s | 是 | 否 | 否 |
| 8 | `xqly.credential.unlock` | credential | 30s | 否 | 否 | 否 |
| 9 | `xqly.devportal.connect` | network:devportal | 30s | 否 | 否 | 是 |
| 10 | `xqly.devportal.send` | network:devportal | 30s | 否 | 否 | 是 |
| 11 | `xqly.devportal.approveTool` | user_approval | 30s | 否 | 是 | 否 |
| 12 | `xqly.file.pickSave` | file:export | 30s | 否 | 否 | 否 |
| 13 | `xqly.clipboard.copy` | clipboard | 1s | 否 | 否 | 否 |
| 14 | `xqly.ota.check` | update | 10s | 是 | 否 | 否 |
| 15 | `xqly.telemetry.report` | telemetry:optional | 2s | 是 | 否 | 否 |
| 16 | `xqly.error.report` | diagnostics | 3s | 是 | 否 | 否 |

**默认权限只有 6 项**：`public` / `ui` / `nav` / `link` / `update` / `clipboard`。
`credential`、`network:devportal`、`user_approval`、`file:export`、`telemetry:optional`、
`diagnostics` 一律不默认授予 → **越权调用 100% 拒绝**（`PERMISSION_DENIED`）。
宿主不另起权限集合，直接复用 `BridgeV1.DEFAULT_PERMISSIONS`（Dart / Java / 宿主同一真源）。

其余硬约束：消息 ≤8192B、参数键 ≤32 且不含危险片段、参数值 ≤2048、
id 格式与去重、同方法 120ms 限流、`url`/`path`/`appId` 受限键专项校验、
离线网络方法降级、遥测需显式开启、审批需用户已确认。

---

## 4. 端点策略与 Deep Link

- 仅 `wss` / `sse` / `https`；端口白名单 `{443, 8443}`；主机须为合法公网域名。
- 私网 / 环回 / 链路本地 / `.local` / `.internal` / 纯 IP / 通配 / 明文一律拒绝。
- 端点必须在**用户保存的 allowlist** 内，默认 allowlist 为空 → 显示「未配置」。
- `xqly://app/dev-portal` 与 `xqly://app/dev-portal/session/<id>` 为仅有的深链，
  查询键只允许 `view` / `focus`；拒绝 `..`、百分号编码绕过、反斜杠与控制字符。
- 证书 pin 可选，须为 64 位小写 hex。**只保存主机配置，绝不保存密钥 / Token / 凭据**。

> 修复记录：`normalizePath` 曾把 scheme 的 `xqly://` 折叠成 `xqly:/`，导致所有深链被误拒；
> 端点校验曾让 `BAD_HOST` 抢先吞掉私网原因。两处均已修正并加 Dart / Java 双侧回归用例。

---

## 5. 隐私与离线降级

| 项 | 保证 |
|---|---|
| 密钥落盘 | 无。端点表只存主机；无凭据读取接口 |
| 遥测 | 默认关闭（`telemetry: false`），关闭时 `xqly.telemetry.report` 被拒 |
| 设备标识 | `envInfo` 明确不返回 IMEI / IMSI / 设备 ID / 文件路径 |
| 日志与导出 | `BridgeV1.redact()` 抹除键值、长 hex、`file://` / `content://` 路径；`responseSafe()` 拦截敏感响应 |
| 存储隔离 | 每应用独立 Web 存储分区，注册表校验 |
| 网络请求 | 页面零外部脚本、零 `fetch`、零 `XMLHttpRequest`；不预置服务器 |
| 定时器 | 前端零常驻 `setInterval`；过期调用惰性清理，隐藏浮层不空转 |

**离线空状态**写清三件事：现在没有内容、为什么、下一步做什么，而不是无限转圈：
> 当前处于离线只读模式 → 本模块在离线档案中不提供远程会话与工具执行 →
> 连接已配置且配对确认的远程服务后可使用，或返回应用抽屉、重新检查更新。

---

## 6. 关于页三句话（常驻 UI，不藏在关于页）

> 社区非官方 · 自研远程门户 ｜ 默认零遥测、零密钥落盘 ｜ v1 不内置执行引擎、不加载第三方插件。

改名清单与品牌边界见 [NAMING_CHECKLIST.md](NAMING_CHECKLIST.md)。

---

## 7. 测试覆盖

| 层 | 用例数 | 覆盖 |
|---|---|---|
| Dart 共享核心 | 178 | 注册表 / 端点 / 16 方法 / 越权 / fuzzing / 脱敏 / 状态机 / 设计契约 / 资源 |
| JVM core | 136 | 同一套规则的 Java 镜像 + `OverlayController` 新增 DEV_PORTAL 层互斥 |
| Node 前端桥 | 15 | 外层消息形状 / envelope / 幂等 id / 队列 / 零定时器 / 静态契约 |
| Node 游戏引擎 | 19 | 既有零回归 |
| C++ 平台核心 | 34 | 既有零回归（令牌扩展后仍通过） |

合计 **382 用例**，`build.sh` 12 步门禁全部通过。
