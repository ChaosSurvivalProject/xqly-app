# AI 工作台模组 · 零依赖功能对标说明

适用版本 **v2.4.0**（Android `versionCode=11`；最低 Android 9 / API 28。Windows 安装包自 v2.4.0 起停止提供，本模组仅随 Android 客户端发布）。

## 1. 背景与决策

用户给出上游项目 `dphmoblie/deepseek-harness-android`（下称「harness」），要求"内置"。
经核查，harness 应用代码为 MIT，但**真正提供 AI 能力的运行时**依赖 AGPL-3.0 的
Operit2 工具链、GPL-2.0 的 PRoot、Ubuntu 24.04 ARM64、Node.js 24.19，体积数百 MB，
构建需 Gradle 8.11.1 + NDK + JDK 21 + 固定摘要的 ARM64 二进制。

在会话内无法取得这些二进制的可复现构建证据，直接并入会让 xqly-app 从 MIT 变成
AGPL/GPL 传染，并破坏「零 Gradle、6.5 MB、零第三方运行时库」的定位。
用户明确选择 **零依赖功能对标**：不引入其代码，用 xqly-app 现有零依赖架构重写交互。

## 2. 对标范围（已实现）

| harness 交互 | xqly-app 对标实现 | 数据落盘 |
|---|---|---|
| 会话管理 | `WorkbenchChat` / `ChatStore`：新建、本地草稿/笔记、最近更新排序、上限保护 | 仅内存 |
| 模型供应商 | `WorkbenchProvider`：9 个内置模板 + 自建端点主机校验，只登记"已配置"布尔态 | 仅布尔态，无密钥 |
| 插件管理 | `WorkbenchPlugin`：注册、启停、版本更新与回滚（回滚后禁用） | 仅清单元数据 |
| 文件投递区 | `WorkbenchDropzone`：`root/1` 工作区约束、NoFollow 语义、穿越/编码绕过拒绝，返回稳定标识 | 不读写文件 |
| 应用内自检 | `WorkbenchSelfCheck`：6 项本地能力 + 13 项运行时逐项标注 | 无 |
| 诊断日志 | `WorkbenchLog`：统一脱敏 + 尾部窗口 + 级别/关键字过滤 + 错误特征判读 | 仅内存 |
| 桥与权限 | `xqly-bridge/1` 新增 15 条 `xqly.workbench.*` 本地方法，双重校验 | 无 |

新增应用 `ai-workbench`：独立 `workbench:*` 桥作用域、独立 Web 存储分区，
纳入 `OverlayController` 互斥浮层（返回键 / 首页 / 关闭均能干净收敛），
Dock「工作台」与侧栏「✧ 工作台」双入口。

## 3. 明确不做（边界）

- **不内置** PRoot / Ubuntu / Node.js / Landlock / PTY / ripgrep / 编译器等任何运行时；
  自检中逐项标为 `unavailable`，不伪造「已就绪」。
- **不发起**任何模型 HTTP/WebSocket 请求；不预置服务器、端点白名单默认空。
- **不执行**任何工具、Shell 命令、第三方插件代码；不使用 Shizuku / 无障碍 / 副屏悬浮窗。
- **不接收、不保存、不回显** API Key / 令牌 / 凭据；供应商页只有"是否已配置"布尔状态。
- **不读取**投递区外的任意文件路径；路径只做安全判定并返回哈希标识。
- **默认零遥测**、无后台服务、无常驻定时器。

会话消息标记为 **user / draft / note**，**永不标记为 assistant**——避免把本地草稿伪装成真实 AI 回复。

## 4. 安全与一致性

- Dart `shared/lib/src/workbench_*.dart` 与 Java `src/cn/xqly/app/core/Workbench*.java` 为**双端镜像**，
  字段、状态机、脱敏规则逐项对齐，由 190 + 144 条用例共同守护。
- 统一脱敏覆盖：JSON 键值、裸 `key=value`、`sk-` 厂商前缀、Bearer 令牌、长十六进制串、
  `file://` / `content://` URI。密钥值在任何日志/导出/桥响应中均不可见。
- 工作台方法全部标记为零网络、零审批、非遥测；`provider.mark` 的 allowedKeys 中
  不存在任何 key/secret/token 字段，越权与未知方法 100% 拒绝。

## 5. 许可证边界

xqly-app 对标实现为**自研 MIT 代码**，不复制、不链接、不分发 harness 的任何源码或二进制。
harness 及其上游运行时的许可证与制品哈希记录在其自身 `THIRD_PARTY_NOTICES.md`；
本项目不含这些组件，因此不承担其 AGPL/GPL 义务。
社区非官方标识常驻于应用抽屉、关于页与本报告，不模仿任何厂商商标。

## 6. 验证

- `build.sh` 12 步门禁全过：Dart 190 + JVM 144 + Node 19 + 15 + 7 + C++ 34 = **409 用例**。
- 正式签名 APK，证书指纹 `e1e8edc5…c21c05`，与钉扎一致，可覆盖升级。
- 前端由真实 Chromium 按手机视口加载验证：15 条方法矩阵、自检列表、供应商目录均由真实代码渲染。
