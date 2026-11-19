# 变更日志

所有版本均通过 `build.sh` 12 步构建门禁，Android 产物为同一发布证书签名。

## v2.3.0（本次发布）· AI 工作台（零依赖功能对标）

- 按用户选定方向，以零依赖架构重写 harness 的会话/供应商/插件/投递区/自检/诊断交互，**不引入 AGPL/GPL 代码**。
- 新增应用 `ai-workbench`：互斥浮层、Dock + 侧栏双入口、独立 `workbench:*` 作用域与存储分区。
- 桥协议新增 15 条 `xqly.workbench.*` 本地方法（共 31 条），appId 作用域按注册表动态匹配。
- 修复 Dart `workbenchRedact` 内联 `(?i)` 运行时崩溃与裸 key/sk- 密钥脱敏缺口。
- 测试：Dart 190 + JVM 144 + Node 19 + 15 + 7 + C++ 34 = **409 用例**，全部通过。
- 详见 [docs/RELEASE_NOTES_2.3.0.md](docs/RELEASE_NOTES_2.3.0.md)、[docs/AI_WORKBENCH_MODULE.md](docs/AI_WORKBENCH_MODULE.md)。

## v2.2.0 · 开发者平台模组与统一 UI 契约

- **新增应用类模组 `dev-portal`**：应用注册表、单一 WebView 池路由、`OverlayController` 互斥浮层、
  独立沙箱、底部 Dock 与侧边药丸双入口、离线只读外壳。
- **新增 `xqly-bridge/1`**：16 条方法的 schema / 权限 / 超时 / 幂等 / 审批 / 网络约束，
  外层 `BridgeProtocol` + 内层 `BridgeV1` 双层校验，越权 100% 拒绝。
- **新增端点策略**：WSS/SSE/HTTPS、端口与私网白名单、allowlist、Deep Link 白名单、证书 pin 格式；
  零密钥落盘、默认零遥测。
- **统一 UI 单源 token 扩展**：`designContract()` 契约 v2（四层 token / 组件状态 / 断点 / 动效 /
  三平台映射），生成器一次产出 5 份字节一致的产物，`--check` 接入门禁。
- **修复** `normalizePath` 折叠 scheme `//` 导致 Deep Link 全被误拒；端点校验私网拒绝原因不可达；
  `approveTool` 的 `scope` / `grant` 字段用反；Dart `redact` 的 raw 字符串与 `$1` 插值两处编译错误。
- **测试**：Dart 178 + JVM 136 + Node 19 + 15 + C++ 34 = **382 用例**，全部通过。
- 详见 [docs/RELEASE_NOTES_2.2.0.md](docs/RELEASE_NOTES_2.2.0.md)、
  [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md)、[docs/DEV_PORTAL_MODULE.md](docs/DEV_PORTAL_MODULE.md)。

## v2.1.1 · 紧急修复 UI 层叠 / 无法关闭 / 性能优化

- 抽出零依赖 `OverlayController`，浮层可见性收敛为唯一真源；200 步 fuzz 验证「可见浮层数 ≤ 1」。
- 浮层不透明背景、首页逃生出口、启动页 8s 兜底、加载超时 20s→12s。
- 动画重绘节流、隐藏 WebView 不唤醒、桥接广播只投可见页、资料库搜索防抖与收藏内存覆盖层。
- 详见 [docs/RELEASE_NOTES_2.1.1.md](docs/RELEASE_NOTES_2.1.1.md)。

## v2.1.0 · 双端更新 / 资料库原生化 / 本地优先同步

- 资料库改为纯原生「星穹玻璃」组件；`SyncEngine` 本地 `.xqlypack` 导出/导入，云端默认关闭。
- 三端版本真源统一。详见 [docs/RELEASE_NOTES_2.1.0.md](docs/RELEASE_NOTES_2.1.0.md)。

## v2.0.1 · 双平台基线

- Android 零 Gradle 构建链 + Windows 原生 Win32 外壳 + GitHub Actions CI/Release。
- 详见 [docs/RELEASE_NOTES_2.0.1.md](docs/RELEASE_NOTES_2.0.1.md)。
