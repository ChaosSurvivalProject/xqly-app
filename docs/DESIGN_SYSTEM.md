# 星穹玻璃 · 统一 UI 设计与三端映射（契约 v2）

适用版本 **v2.2.0**。本文是「统一 UI 单源 token」扩展的规范说明，与代码里的
`shared/lib/src/design_tokens.dart` → `shared/tool/gen_tokens.dart` → 五份产物**逐字对应**。
不另起一套色板、不引入第二个真源。

---

## 1. 唯一真源与生成链

```
shared/lib/src/design_tokens.dart          （唯一真源：色值 / 间距 / 圆角 / 字号 / 动效 / 断点 / 契约）
        │  dart tool/gen_tokens.dart ..
        ├── shared/design-tokens.json       （供 Dart / JVM 测试与文档读取）
        ├── assets/design-tokens.css       （Android WebView 页面，含 dev-portal）
        ├── windows/runner/resources/design-tokens.json  （Windows 原生自绘读取）
        ├── tokens.json                    （规范命名产物，与上面 JSON 字节一致）
        └── tokens.css                     （规范命名产物，与 CSS 字节一致）
```

`build.sh` 的 [1/12] 步同时执行生成与 `--check`，任何一端手工改动都会被门禁拦下。
`dart analyze` 也在同一步运行。五份产物由**同一次 `build()` 调用**写出，因此不存在漂移。

---

## 2. 四层 token（contract = 2）

| 层 | 内容 | 代码位置 | 说明 |
|---|---|---|---|
| **primitive** | 深/浅/高对比 × 玻璃/省电 共 6 套主题映射的原子色值与尺寸 | `DesignTokens.all(theme, mode)` | 原始值只在这里定义一次 |
| **semantic** | `--xq-background` / `--xq-surface` / `--xq-text` / `--xq-success` / `--xq-warning` / `--xq-error` / `--xq-focus` / `--xq-brand` | `DesignTokens.toCss()` | 组件只用语义名，不引用原子色 |
| **componentState** | button / field / card 各状态契约、最小触摸目标、焦点描边 | `DesignTokens.componentStates` / `minTouchTarget` | 三端同一套状态语义 |
| **platform** | `android` / `webview` / `windows` 的落点映射 | `DesignTokens.platforms` | 明确每层的消费方，不做平台私有色板 |

`designContract()` 输出的 `schema=2`，包含 `layers`、`grid`（8/12 栏）、`radius`、`type`、
`lineHeights`、`motion`、`breakpoints`、`elevation`、`componentStates`、`platforms`、
`contrast`、`feedback`、`reducedMotion`。测试逐项断言，缺一项即失败。

---

## 3. 组件状态契约

| 组件 | 状态 | 硬约束 |
|---|---|---|
| button | `idle` / `loading` / `pressed` / `disabled` / `focusVisible` | `loading` 保持原宽高且不可再点；`disabled` 降对比并去阴影；`pressed` 用表面提亮而非纯透明度叠加；焦点态用 `--xq-focus` 2px 描边 |
| field | `default` / `focused` / `filled` / `error` / `disabled` | 错误态用 `--xq-error`，不靠颜色单独传达，同时给文案 |
| card | `rest` / `hover` / `pressed` / `selected` | 暗色下用 1–3% 亮度 + 1dp 描边，不用厚重投影 |

最小触摸目标 **48dp/px**（`--xq-touch-scale` 可整体放大），正文最大阅读宽度 760px。
暗色对比度：普通文本 ≥ 4.5:1，大文本 ≥ 3.0:1。

前端实现见 `assets/apps/dev-portal/app.css`：全部用 `var(--xq-*)`，**不出现任何 hex 色值**
（Node 静态测试会扫描 `#[0-9a-fA-F]{3,8}` 并拒绝）。

---

## 4. 断点、动效与反馈

| 项 | 值 | 用途 |
|---|---|---|
| 断点 | 320–519 窄屏 / 0–599 手机 / 600–1199 折叠与平板 / ≥1200 桌面 | 应用抽屉 1 列 → 2 列；不按设备名硬编码 |
| 动效 | fast 120ms / base 200ms / slow 300ms；页面切换 ≤ 300ms | 强调动效 600ms 仅用于启动 |
| 减弱动效 | `prefers-reduced-motion` 下时长归零、仅透明度、无位移 | 系统级无障碍要求 |
| 反馈位置 | Android 原生 `bottom-bar`；WebView 页面 `top-safe-bar` | 同一队列语义，不各写一套 |
| 反馈时长 | 危险 6000ms / 普通 4000ms；同时最多 1 条；队列上限 16 | 高优先级抢占，长文本可手动关闭 |

---

## 5. 三端映射清单

| Token / 能力 | Android 原生（Java View） | WebView 页面（CSS/JS） | Windows 原生（C++17 / Win32） |
|---|---|---|---|
| 色值与主题 | `DesignTokens.java` 的 `palette()` | `design-tokens.css` + `data-theme` / `data-mode` | `design_tokens.cpp` 读 `design-tokens.json` |
| 材质 | `GlassDrawable` / `GlassCard` 自绘 | 分层渐变 + `--xq-glass-alpha`，不用 `backdrop-filter`（移动端重绘代价高） | Win32 自绘 |
| 圆角 / 间距 | `dp()` / `sdp()` 缩放 | `--xq-radius-*` / `--xq-gap-*` | 按 DPI 换算 |
| 组件状态 | 原生 `GlassButton` 五态 | `.btn` 五态 + `:focus-visible` | 原生按钮状态 |
| 断点 | `isTablet` / `isLandscape` / `foldUnfolded` | CSS `@media (min-width: 640px)` | `WM_DPICHANGED` / 窗口尺寸 |
| 反馈 | 原生 Toast / 底部条 | 顶部安全栏 `#feedback` | 原生提示条 |
| 应用注册表 | `AppRegistry.java` | `apps.dart`（同一份数据语义） | `AppRegistry` 语义镜像 |
| 桥协议 | `BridgeProtocol` + `BridgeV1`（Java） | `bridge-shim.js` + `bridge-v1.js` | `bridge_protocol.dart` / `bridge_v1.dart` |

三端的**规则实现互为镜像，判定结果必须等价**：同一条非法消息在 Dart / Java / Node
三套测试中都被拒绝，且拒绝原因一致（用例逐项覆盖）。

---

## 6. 硬约束

1. 任何一端不得硬编码色值或另建 token 文件；新增语义 token 必须回到 `design_tokens.dart`。
2. `--check` 是构建门禁的一部分，产物与真源不一致直接失败。
3. 主题切换只改 `data-theme` / `data-mode` 属性，不重建 DOM、不三套页面。
4. 组件状态必须同时覆盖键盘焦点与减弱动效，不能只做鼠标态。
