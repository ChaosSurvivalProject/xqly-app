# 星穹旅驿 v2.4.0 · Android 9 基线 · 停止 Windows 安装包

Android `versionCode=11` / `versionName=2.4.0`，最低系统 **Android 9（API 28）**。

本次为平台基线调整与发布链变更，功能集延续 v2.3.0（开发者平台 + AI 工作台）。

## 平台基线

- **Android 最低系统由 7.0（API 24）提升至 9.0（API 28）**：
  `AndroidManifest.xml` 的 `minSdkVersion` 24→28，`build.sh` 中 `aapt2 link --min-sdk-version`
  与 `d8 --min-api` 同步改为 28。Android 7/8 设备将无法安装本版本，请升级系统或使用网页版。
- 目标 SDK 仍为 35；`javac -source/-target 8` 保持不变（与最低 API 无冲突）。
- 星穹玻璃绘制层注明最低支持 Android 9.0（API 28）。

## 停止提供 Windows 安装包（重要公告）

- **自 v2.4.0 起不再构建、不再发布任何 Windows 安装包**（MSIX 安装版 / ZIP 便携版 / `xqly.exe`）。
  后续版本**只提供 Android APK**。
- 发布流水线 `.github/workflows/release.yml` 已移除 Windows job 与 Windows 资产拷贝；
  CI `.github/workflows/ci.yml` 不再上传 `windows-exe` 产物。
- Win32 源码、`windows/packaging/` 打包配置与签名脚本作为**历史归档**保留在 `windows/` 目录，
  供代码审计参考，不再维护、不参与发布。
- 历史版本的 Windows 安装包仍保留在旧 Release（v2.3.0 及更早）中可供下载。
- 需要桌面使用的用户请改用官网网页版 [xqly.xt91tv.shop](https://xqly.xt91tv.shop)。

## 离线内容与仓库链接

- 内置「全站档案」与离线页脚加入开源仓库链接
  [github.com/ChaosSurvivalProject/xqly-app](https://github.com/ChaosSurvivalProject/xqly-app)。
- 随包内容清单 `MANIFEST.json` 已随离线内容更新重新生成并纳入 SHA-256 校验。

## 版本与验证

- 六处版本真源统一：AndroidManifest / build.sh / MainActivity / Dart identity /
  Windows 源码头（仅归档）与测试断言，均为 `2.4.0`（Android code 11，Windows 四段 2.4.0.0）。
- `build.sh` 12 步门禁全过；**409 用例**：Dart 190 + JVM 144 + Node 19 + 15 + 7 + C++ 34。
- 正式签名 APK 证书 `e1e8edc5…c21c05`，与钉扎一致，可覆盖升级（Android 9+）。

## 权益与边界

客户端自研代码以 MIT 开源；官网文字、图片、字体、商标及站点素材不在 MIT 授权范围内，
详见 [NOTICE.md](../NOTICE.md) 与 [THIRD_PARTY.md](THIRD_PARTY.md)。
本版本为客户端平台基线调整，不包含新的模型推理能力；AI 工作台仍为零依赖功能对标。
