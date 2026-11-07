# 星穹旅驿 v2.1.0 · Windows + Android

> 草稿发布说明。正式签名 APK 由本仓库外发布密钥签名（证书自 v1.1.0 起一致，可覆盖升级）；Windows 二进制由 CI 在 `windows-latest` 生成，无正式代码签名证书时标注 `UNSIGNED` 或测试签名，不伪装正式版。

## 本版本重点

- **资料库原生 UI 重写**：资料库（Library）改为纯原生「星穹玻璃」组件，不再加载 WebView 壳；复用同一套本地检索 `SearchIndex` 与用户数据 `UserData`。
- **性能优化**：收藏/取消收藏不再重建整张倒排索引，改为 `favSet` 覆盖层 + 搜索时过滤，收藏高频操作成本从 O(全量文档·词) 降到 O(结果数)。
- **本地优先 + 可选云端同步引擎** `SyncEngine`：默认只做本地 `.xqlypack` 导出/导入（带 schema、设备 id、生成时间、SHA-256 校验和，篡改/截断/降 schema 整体拒绝）；云端传输为可插拔接口且默认关闭，绝不自行联网、绝不硬编码端点、绝不伪造凭据。`cloud_sync` 功能开关默认 `false`。
- **版本号统一提升**：Android `versionCode=7` / `versionName=2.1.0`，Windows `2.1.0.0`，与 Dart 共享核心、构建脚本、资源头六处真源一致，构建门禁逐字校验。

## 测试与构建

- 测试合计 **315 个用例**：Dart 152 + JVM 110 + Node 19 + C++ 34，全部通过。
- `build.sh` 12 步门禁全部通过；APK 内 `MANIFEST.json` / `design-tokens.css` 字节一致；APK 经 `apksigner verify` 校验。
- 本机构建环境：Android SDK build-tools 35.0.0 / platforms android-35、Dart 2.17、Node 22、JDK（javac）、Python 3.11。

## 资产校验

所有资产在 Release 中附带 `SHA256SUMS.txt`，下载后请 `sha256sum -c SHA256SUMS.txt` 校验。

- `xqly-app-v2.1.0.apk`（OTA 主资产，命名受更新链约束）
- `星穹旅驿-2.1.0-android-universal-release.apk`（中文别名，仅附加下载项）
- Windows 安装版（`.msix`，测试签名）/ 便携版（`.zip`）由 CI 在 `windows-latest` 生成

## 知道的边界（如实标注）

- 游戏（方块合成）与离线档案（archive）当前仍由本机 WebView 承载原生页面，未做与资料库同级别的原生组件重写；属下一阶段计划项。
- **Windows 二进制 CI 状态（v2.1.0 已修复）**：此前发布流水线因硬编码 `Visual Studio 17 2022` 生成器、且 MSIX 清单缺 `runFullTrust` 能力而失败；v2.1.0 改为让 CMake 自动探测生成器、并补上 `runFullTrust` 能力后，CI 的 `windows-latest` 运行器（Visual Studio Enterprise 2026）可正常编译 `xqly.exe`、跑通冒烟测试并产出 MSIX / 便携 ZIP。Windows 包目前为**测试签名**（组织未配置 `WINDOWS_SIGNING_PFX` 证书），Release 中明确标注「测试签名，非公开正式版」，不伪装正式版；正式发布需配置代码签名证书 + RFC3161 时间戳。
- 本机 Linux 开发沙箱无 MSVC / Windows SDK / 代码签名证书，不产生 `xqly.exe`、MSIX 或便携 ZIP 二进制；源码完整可审计，二进制由 CI 产出。

## 升级说明

覆盖安装即可；签名证书与历史版本一致，不会冲突。离线镜像内容版本以 `assets/MANIFEST.json` 为准。
