# 星穹旅驿 v2.3.0 · AI 工作台（零依赖功能对标）

Android `versionCode=10` / `versionName=2.3.0`，Windows `2.3.0.0`。

本次按用户选定方向，以 xqly-app 现有零依赖架构重写 `dphmoblie/deepseek-harness-android`
的界面与本地数据交互，**不引入其 AGPL/GPL 运行时代码**。

## 新增

- **应用 `ai-workbench`**：Dock「工作台」+ 侧栏「✧ 工作台」双入口，纳入 `OverlayController`
  互斥浮层，独立 `workbench:*` 作用域与 Web 存储分区。
- **本地会话**：新建、用户草稿/离线笔记/占位草稿、最近更新排序、消息与会话上限。
  消息永不标记为 assistant，不伪装成真实 AI 回复。
- **模型供应商目录**：DeepSeek / OpenAI / Anthropic / Gemini / OpenRouter / Groq / xAI /
  Mistral / 自建端点共 9 项；只登记"已配置"布尔态，**零密钥接收与落盘**。
- **插件清单**：注册、启停、版本更新与回滚，回滚后自动禁用；只登记元数据，**不加载第三方代码**。
- **投递区路径校验**：`root/1` 工作区约束，拒绝绝对路径、目录跳转、编码斜杠、
  Windows 盘符与工作区外路径，返回稳定哈希标识，不读写文件。
- **环境自检**：6 项本地能力 + 13 项运行时逐项标注 `unavailable`，如实呈现能力边界。
- **诊断日志**：统一脱敏、尾部窗口、级别/关键字过滤、已知错误特征判读。

## 安全与边界

- 不内置 PRoot / Ubuntu / Node / Landlock 等任何运行时；不发起模型请求、不执行工具、
  不使用 Shizuku/无障碍、默认零遥测、无常驻定时器。
- 统一脱敏覆盖 JSON 键值、裸 `key=value`、`sk-` 前缀、Bearer、长哈希与本地 URI。
- 越权 / 未知方法 / 外来 appId 100% 拒绝；工作台 15 条方法全为零网络、零审批、非遥测。

## 修复

- Dart `workbenchRedact` 使用不受支持的内联 `(?i)` 正则标志导致运行时崩溃 —— 改为
  `caseSensitive: false` 命名参数（与 Java 侧行为对齐）。
- 脱敏缺口：裸 `key=value` 与 `sk-` 前缀密钥未覆盖 —— 补齐双端规则。

## 验证

`build.sh` 12 步门禁全过，**409 用例**：Dart 190 + JVM 144 + Node 19 + 15 + 7 + C++ 34。
正式签名 APK 证书 `e1e8edc5…c21c05`，与钉扎一致，可覆盖升级。

详见 [AI 工作台说明](AI_WORKBENCH_MODULE.md)、[设计系统](DESIGN_SYSTEM.md)、
[第三方声明](THIRD_PARTY.md)。
