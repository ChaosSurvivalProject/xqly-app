# 官方发布与权利声明

本仓库是 **星穹旅驿服务器运营团队官方发布**的双平台客户端源码与产物，官网为 [xqly.xt91tv.shop](https://xqly.xt91tv.shop)。

当前版本 **v2.1.0**（Android `versionCode=7` / Windows `2.1.0.0`）：原生 Android 客户端 + 原生 Windows 客户端 + 纯 Dart 双平台共享业务核心。

---

## 内容与授权划分

| 类别 | 路径 | 权利归属 | 授权 |
|---|---|---|---|
| 客户端自研代码 | `src/`、`shared/`、`windows/`、`build.sh`、`tools/`、`scripts/`、`AndroidManifest.xml`、`res/` 自绘图标与样式 | 星穹旅驿运营团队 | MIT（见 `LICENSE`） |
| 测试与文档 | `tests/`、`docs/`（除 `全站内容档案.html`）、`.github/workflows/` | 星穹旅驿运营团队 | MIT |
| 离线小游戏与内置页面 | `assets/game.html`、`assets/library.html`、`assets/library.css`、`assets/game-engine.js`、`assets/bridge-shim.js`、`assets/design-tokens.css` | 星穹旅驿运营团队 | MIT |
| 站点素材与内容 | `assets/site/`、`assets/data/`、`assets/archive.html`、`docs/全站内容档案.html` | 星穹旅驿运营团队 | **版权所有，不在 MIT 授权范围内** |
| 品牌图标 | `windows/runner/resources/xqly.ico`、`windows/packaging/msix/Assets/*.png` | 星穹旅驿运营团队（本项目自绘） | MIT |

客户端自研代码欢迎按 MIT 协议使用、修改与再分发。**官网文字、图片、字体、商标及站点素材版权归运营团队所有，转载或二次分发请另行取得许可。** 本项目自有界面使用「星穹玻璃」设计语言与自绘图标，不模仿任何厂商品牌，也不含任何厂商商标资产。

---

## 离线镜像构成

- **来源**：星穹旅驿官网 `https://xqly.xt91tv.shop`
- **镜像时间**：2026-11-04 ~ 2026-11-05
- **构成**：
  - 主站（Vite + Vue 单页应用）的 HTML、JS bundle、懒加载 chunk、CSS、字体、图片
  - `/wiki/`（VitePress）与 `/admin/`（pure-admin）两个子应用的静态资源
  - 17 个页面的渲染正文（约 11,143 字）与 16 组接口 JSON 快照
- **合计**：202 个静态文件 / 约 10.4 MB；当前 `assets/MANIFEST.json` 记录 228 个随包文件（含清单以外的自研页面、令牌 CSS 与索引）

镜像用于客户端离线浏览，随官方客户端一同分发。镜像保持抓取时的原始字节以保证离线渲染一致，其中残留的厂商字体回退与 UA 技术标识不属于自有界面品牌描述，详见 `docs/THIRD_PARTY.md`。

---

## 运维信息提示

镜像中的论坛运维公告包含服务器路径、备份文件名与部署命令等细节。本仓库为公开仓库，这些信息会随之公开；运营团队如需移除，可运行 `python3 tools/redact.py --apply` 批量脱敏后重新发布版本。

---

## 发布产物与签名

| 平台 | 产物 | 状态 |
|---|---|---|
| Android | 正式签名 APK（`xqly-app-v2.1.0.apk`） | 使用仓库外私有发布密钥签名，证书自 v1.1.0 起一致，可覆盖升级 |
| Windows | 签名 MSIX 安装版 / 便携 ZIP | 由 `.github/workflows/release.yml` 在 `windows-latest` 生成；**无正式代码签名证书时必须标注测试签名或 UNSIGNED**，不得伪装正式版 |

发布密钥、证书、Token、口令一律不进入仓库或日志。第三方组件与许可证见 `docs/THIRD_PARTY.md`，软件物料清单见 `docs/dual-platform/SBOM.md`。

已签名产物见 [Releases](https://github.com/ChaosSurvivalProject/xqly-app/releases)。
