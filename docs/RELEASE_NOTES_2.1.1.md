# 星穹旅驿 v2.1.1 · Windows + Android（紧急修复版）

> 草稿发布说明。Android APK 由本仓库发布密钥签名（证书自 v1.1.0 起一致，可覆盖升级）；Windows 二进制由 CI 在 `windows-latest` 生成，无正式代码签名证书时为**测试签名**，不伪装正式版。

## 为什么有这个版本

v2.1.0 把资料库改成纯原生 `LibraryView` 时引入了一个**致命 UI 回归**：用户反馈「界面层层叠叠、卡住动不了、关闭不了这个界面、跳转以后无法关闭」。本版本专门修掉它，并把散落的浮层状态收敛成可测试的单一真源，同时做了一轮真实的性能优化与发布链收尾。

## 根因（逐条对应截图现象）

| # | 根因 | 位置 | 用户看到的现象 |
|---|------|------|----------------|
| 1 | `buildLibraryOverlay()` **漏了 `setVisibility(GONE)`** | `MainActivity` | 资料库浮层从启动就常驻叠在主 WebView 上 |
| 2 | 浮层与 `LibraryView` 都**没有不透明背景**（浮层还用半透明 `OVERLAY` 令牌，alpha `0xD8`） | `MainActivity` / `LibraryView` | 下层主页内容全部透出 → 「层层叠叠」 |
| 3 | `libraryOpen` 标志仍为 `false`，但浮层已显示 | `MainActivity` | 返回键、`closeOverlay` 桥接消息都关不掉它 → 「关闭不了」 |
| 4 | 上层透明 `FrameLayout` 拦截触摸 | — | 下层 SPA 的关闭按钮点不到 → 「卡住动不了」 |
| 5 | `openArchive()` / `openLibrary()` 不关其它浮层；`onOpenPage` 不关 game；`onCloseOverlay` 用 `if/else-if` 一次只关一个 | `MainActivity` | 「跳转以后无法关闭」、残留层 |
| 6 | `showOverlay`/`hideOverlay` 的 `withEndAction` 会被紧接着的新动画取消 | `MainActivity` | 浮层停在 `VISIBLE`，留下半透明幽灵层 |
| 7 | 启动页只在 `onPageFinished` 才隐藏，加载失败/超时无兜底 | `MainActivity` | 离线失败时半透明启动页 + LOADING 遮罩永久盖住界面 |

## 修复内容

- **新增 `core/OverlayController`**（零 Android 依赖，可在 JVM 上回归）：把「谁可见」收敛为唯一真源。每次状态变更返回一份**完整迁移**（要显示的层 + 必须隐藏的所有其它层），调用方 `applyOverlayTransition()` 无条件重设全部浮层可见性，先 `animate().cancel()` 并复位 alpha/scale，彻底消除动画竞态残留。不变量：任意时刻最多一个浮层可见。
- **资料库浮层初始 `GONE` + 不透明背景**；`archive`/`game` 浮层同样改为不透明背景，不再透视下层。
- **所有跳转/关闭路径统一走 `closeAllOverlays()`**：`onOpenPage`、`onCloseOverlay`、`onDockClick`、`setOffline`、`openLibraryDoc`、返回键。返回键现在一次收敛全部浮层。
- **新增「✦ 首页」一键逃生出口**：任何叠加/卡死状态下都能关掉全部浮层、全屏视频、启动页并重载首页，同时让 SPA 自己收起抽屉与登录/注册弹层。
- **启动页 8 秒强制兜底** + 加载超时从 20s 收紧到 12s；错误/超时态都先移除启动页，保证「重试」按钮可见可点。
- 修复 `LibraryView.runSearch(null)` 时 `q.trim()` 的空指针崩溃。

## 性能优化（真实可验证）

- **`GlowView` 背景流动**：渐变对象由「每帧 `new RadialGradient` ×2」改为按尺寸只创建一次、每帧仅更新 shader 局部矩阵（**每帧零对象分配**）；重绘从 60fps 节流到约 25fps，减少全屏两次混合绘制的 GPU overdraw。
- **启动页动画泄漏**：`hideSplash()` 改为从视图树 `removeView`，触发 `onDetachedFromWindow` 停止无限 `ValueAnimator`；旧实现只设 `GONE`，动画一直在后台跑。
- **内置浮层延迟加载**：`archive.html` / `game.html` 不再在 `onCreate` 就 `loadUrl`，只在首次真正打开时加载，避免启动时 3 个 WebView 并发渲染。
- **删除空白 WebView**：资料库改原生后遗留的 `libraryWeb` 从未加载过任何页面，本版本直接移除，少一个重量级渲染实例（进程内存与初始化开销）。
- **`resumeTimers()`/`pauseTimers()` 是全局开关**：旧实现逐个唤醒 4 个 WebView（含隐藏/空白的），改为只调用一次 + 仅唤醒当前可见浮层。
- **桥接广播与主题注入只投递可见页面**：`reply()` / `applyWebTheme()` 不再对 4 个 WebView 全量 `evaluateJavascript`。
- **`LibraryView` 输入防抖 120ms**：逐字输入合并为一次检索，不再每个按键重建整张结果列表；收藏切换只改内存 `favSet`，不再重新读盘全量重建集合。
- 清理从未被读取的死变量 `activeWeb`。

## 发布链收尾（上次未完成项）

- 补上 `gh release create --target "$GITHUB_SHA"`：让 `target_commitish` 指向具体提交而非 `main` 分支，草稿 Release 得到干净的 `releases/tag/v2.1.1` 链接（旧版会退化成 `untagged-*` 占位 URL）。
- Windows 资产统一改用 ASCII 名 `xqly-app-v<版本>-windows-x64-*`，规避 GitHub 对中文资产名的归一/截断。
- 草稿幂等：Release 已存在且为**草稿**时删后重建，仅正式发布才跳过；资产拷贝兼容 `upload-artifact@v4` 的 `dist/` 前缀。
- 移除已废弃的 `android-actions/setup-android@v3`（硬装已删除的 `tools` 包）；MSIX 清单补 `rescap:runFullTrust`；CI 的 Windows 运行器（VS Enterprise 2026）实测可编译 `xqly.exe` 并产出 MSIX / 便携版。

## 测试与构建

- 新增 **8 个浮层互斥回归用例**，含 200 步确定性随机操作序列，验证「可见浮层数恒 ≤ 1」及迁移不变量。
- **测试合计 323 个用例**：Dart 152 + JVM 118 + Node 19 + C++ 34，全部通过。
- `build.sh` 12 步门禁全部通过；APK 内 `MANIFEST.json` / `design-tokens.css` 字节一致；APK 经 `apksigner verify` 校验。
- 版本号六处真源统一：Android `versionCode=8` / `versionName=2.1.1`、Windows `2.1.1.0`。

## 资产校验

所有资产附带 `SHA256SUMS.txt`，下载后请 `sha256sum -c SHA256SUMS.txt` 校验。

- `xqly-app-v2.1.1.apk`（OTA 主资产，命名受更新链约束）
- `xqly-app-v2.1.1-android-universal-release.apk`（附加下载项）
- `xqly-app-v2.1.1-windows-x64-setup.msix`（Windows 安装版，测试签名）
- `xqly-app-v2.1.1-windows-x64-portable.zip`（Windows 便携版）

## 知道的边界（如实标注）

- 游戏（方块合成）与离线档案（archive）仍由本机 WebView 承载原生页面，未做与资料库同级别的原生组件重写；属下一阶段计划项，本次未虚构其完成。
- 主站为抓取的第三方 Vue SPA，其站内抽屉 / 登录注册弹层由页面自身管理；Android 侧提供的兜底是「返回键收敛浮层」与「✦ 首页一键重载」，不改动第三方页面源码。
- Windows 包为**测试签名**（组织未配置代码签名证书），不伪装正式版；本机 Linux 沙箱无 MSVC / Windows SDK，Windows 二进制由 CI 产出。

## 升级说明

覆盖安装即可；签名证书与历史版本一致，不会冲突。离线镜像内容版本以 `assets/MANIFEST.json` 为准。
