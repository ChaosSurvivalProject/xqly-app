# 软件物料清单（SBOM）

适用版本 **v2.0.1** · 格式 **SPDX 2.3**

本文件是人读版软件清单；机器可读版 `SBOM.spdx.json` 由 `tools/gen_release_assets.py` 从**真实存在的产物文件**生成，随每个 GitHub Release 一并发布。生成器只扫描目录里实际存在的文件，不创建占位假文件、不虚构 Windows 产物。

```bash
python3 tools/gen_release_assets.py release/ 2.0.1
# 产出：release/SHA256SUMS.txt、release/SBOM.spdx.json
```

---

## 1. 主包

| 字段 | 值 |
|---|---|
| SPDXID | `SPDXRef-Package-xqly` |
| 名称 | 星穹旅驿 |
| 版本 | 2.0.1（Android `versionCode=6` / Windows `2.0.1.0`） |
| 许可 | MIT（仅客户端自研代码） |
| 版权 | Copyright (C) 2026 星穹旅驿运营团队 |
| 备注 | 官网文字/图片/字体/商标/站点素材不在 MIT 授权范围内，见 `NOTICE.md` |

## 2. 组件清单

`packages[].filesAnalyzed=false`：这些组件以源码/构建工具/系统组件形式参与，不以「逐文件分析」方式声明。许可证标识中的 `LicenseRef-*` 为专有许可引用，不代表开源授权。

| SPDXID 后缀 | 组件 | 版本 | 许可证 | 角色 |
|---|---|---|---|---|
| `-component-1` | `xqly_core`（纯 Dart 双平台共享核心） | 2.0.1 | MIT | 运行时逻辑，零第三方依赖 |
| `-component-2` | `xqly-android`（原生 Java + WebView） | 2.0.1 | MIT | Android 客户端 |
| `-component-3` | `xqly-windows-core`（C++17 平台无关核心） | 2.0.1 | MIT | Windows 客户端逻辑，真实编译+测试 |
| `-component-4` | `xqly-windows-shell`（Win32 外壳） | 2.0.1 | MIT | Windows 原生窗口 |
| `-component-5` | Android SDK build-tools | 35.0.0 | `LicenseRef-Android-SDK` | 仅构建期 |
| `-component-6` | Android platform | android-35 | `LicenseRef-Android-SDK` | 仅构建期 |
| `-component-7` | OpenJDK / javac | 17 | GPL-2.0-only WITH Classpath-exception-2.0 | 仅构建期 |
| `-component-8` | Dart SDK | 2.17.0 | BSD-3-Clause | 构建与测试期 |
| `-component-9` | Node.js | 22 | MIT | 仅游戏引擎测试 |
| `-component-10` | Python | 3.11 | PSF-2.0 | 索引/清单/图标/发布资产生成 |
| `-component-11` | CMake | ≥3.15 | BSD-3-Clause | 仅构建期 |
| `-component-12` | Visual Studio 2022 MSVC | 17.x | `LicenseRef-MSVC` | 仅 Windows 构建期 |
| `-component-13` | Windows SDK | 10.x | `LicenseRef-Windows-SDK` | 仅 Windows 打包与签名期 |
| `-component-14` | Android System WebView | system | `LicenseRef-System-Component` | 运行时系统组件，不随包分发 |
| `-component-15` | WebView2 Runtime | system-optional | `LicenseRef-System-Component` | Windows 可选运行时，不静默下载 |
| `-component-16` | Pillow | preinstalled | HPND | 仅构建期生成自绘图标 |

关系：`SPDXRef-DOCUMENT DESCRIBES SPDXRef-Package-xqly`，主包 `CONTAINS` 每个真实产物文件（`files[]` 中带 SHA-256 校验和）。

> 逐项用途与「是否随包分发」见 [第三方依赖清单](../THIRD_PARTY.md)。

## 3. 本版本产物文件

| 产物 | 生成方 | 本机（Linux 沙箱） | 命名规则 |
|---|---|---|---|
| Android 正式签名 APK | `build.sh` + 仓库外发布密钥 | ✅ 真实生成 | `xqly-app-v2.0.1.apk`（OTA 主资产）、`星穹旅驿-2.0.1-android-universal-release.apk`（下载别名） |
| Windows 签名 MSIX 安装版 | CI `windows-latest` + `makeappx.exe` | ❌ 本机无工具链，不伪造 | `星穹旅驿-2.0.1-windows-x64-setup.msix` |
| Windows 便携 ZIP | CI `windows-latest` + `Compress-Archive` | ❌ 本机无工具链，不伪造 | `星穹旅驿-2.0.1-windows-x64-portable.zip` |
| `SHA256SUMS.txt` / `SBOM.spdx.json` | `gen_release_assets.py` | ✅ 对真实存在的资产生成 | 随 Release |

**OTA 主资产名由更新链硬约束为 `xqly-app-v<version>.apk`**（见 `shared/lib/src/identity.dart`），中文名只是给用户看的附加下载项，不参与自动更新解析。

本机生成的 `SHA256SUMS.txt` 只包含当时真实存在的资产；CI 生成 Windows 产物后会重新运行生成器，把三类产物一并纳入 Release 的清单。**因此以 Release 中的 `SHA256SUMS.txt` 为最终准绳。**

## 4. 构建可复现性（如实边界）

| 项 | 状态 |
|---|---|
| 工具链版本锁定 | ✅ CI 中显式锁定 Dart / build-tools / platform / JDK / Node |
| 无网络依赖 | ✅ `shared/` 零依赖且 `dart pub get --offline`；Android 零 Gradle、不联网 |
| 内容清单幂等 | ✅ `tools/gen_manifest.py` 在内容未变化时保留原 `MANIFEST.json`（含 `version`），不会因重复构建漂移 |
| 生成时间戳可控 | ✅ `gen_release_assets.py` 支持 `SOURCE_DATE_EPOCH` 固定 SPDX 创建时间 |
| 源码→资源字节一致 | ✅ `build.sh` 第 11 步把 APK 内 `MANIFEST.json` / `design-tokens.css` 与源码树做 `cmp` 比对 |
| **APK 字节级可复现** | ❌ 未达成：`apksigner` 的 v1 签名含时间戳，且未固定签名时间；相同源码重复签名可能得到不同 SHA-256。**内容一致但哈希不保证一致**，Release 资产一律以当次 `SHA256SUMS.txt` 为准 |

任何「相同哈希即可复现」的说法都不成立，特此标注。

## 5. 校验方法

```bash
# 1) 摘要
(cd release && sha256sum -c SHA256SUMS.txt)

# 2) Android 签名与版本
apksigner verify --verbose xqly-app-v2.0.1.apk
apksigner verify --print-certs xqly-app-v2.0.1.apk | grep 'SHA-256'
aapt dump badging xqly-app-v2.0.1.apk | head -3

# 3) Windows 签名（仅有正式证书时）
signtool verify /pa /v 星穹旅驿-2.0.1-windows-x64-setup.msix
```

Android 发布证书 SHA-256 钉扎值：`e1e8edc56e9700a27ab2cd4ba5f45fa0ba1bd9a88e4a261de7ef3684b3c21c05`，与 v1.1.0 起所有版本一致，可覆盖升级。
