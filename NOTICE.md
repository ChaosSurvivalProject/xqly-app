# 官方发布与权利声明

本仓库是 **星穹旅驿服务器运营团队官方发布**的 Android 客户端源码与产物，官网为 [xqly.xt91tv.shop](https://xqly.xt91tv.shop)。

## 内容与授权划分

| 类别 | 路径 | 权利归属 | 授权 |
|---|---|---|---|
| 客户端自研代码 | `src/`、`build.sh`、`scripts/`、`tools/`、`AndroidManifest.xml`、`res/` 自绘图标与样式 | 星穹旅驿运营团队 | MIT（见 `LICENSE`） |
| 离线小游戏 | `assets/game.html` | 星穹旅驿运营团队 | MIT（同上） |
| 站点素材与内容 | `assets/site/`、`assets/data/`、`assets/archive.html`、`docs/全站内容档案.html` | 星穹旅驿运营团队 | 版权所有，不在 MIT 授权范围内 |

客户端代码欢迎按 MIT 协议使用、修改与再分发。官网文字、图片、字体、商标及站点素材版权归运营团队所有，转载或二次分发请另行取得许可。

## 离线镜像构成

- **来源**：星穹旅驿官网 `https://xqly.xt91tv.shop`
- **镜像时间**：2026-11-04 ~ 2026-11-05
- **构成**：
  - 主站（Vite + Vue 单页应用）的 HTML、JS bundle、懒加载 chunk、CSS、字体、图片
  - `/wiki/`（VitePress）与 `/admin/`（pure-admin）两个子应用的静态资源
  - 17 个页面的渲染正文（约 11,143 字）与 16 组接口 JSON 快照
- **合计**：202 个静态文件 / 约 10.4 MB；APK 内置 assets 共 220 个文件（含 `game.html`）

镜像用于客户端离线浏览，随官方客户端一同分发。

## 运维信息提示

镜像中的论坛运维公告包含服务器路径、备份文件名与部署命令等细节。本仓库为公开仓库，这些信息会随之公开；运营团队如需移除，可运行 `python3 tools/redact.py --apply` 批量脱敏后重新发布版本。

## 版本

当前 **v1.1.0**：在线 / 离线双模式均可完整使用，新增离线小游戏「方块合成」。已签名 APK 见 [Releases](https://github.com/ChaosSurvivalProject/xqly-app/releases)。
