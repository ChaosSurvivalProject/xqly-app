# 星穹旅驿 · Android 客户端

一个把 [xqly.xt91tv.shop](https://xqly.xt91tv.shop) 完整打包成 Android 应用的工程：原生液态玻璃 UI、底部 Dock 导航、**在线直连 + 离线全站镜像**双模式，以及内置的「全站内容档案」。

![界面预览](docs/界面预览.png)

## 特性

- **液态玻璃界面**：顶部控制条已改为可换边的**侧边玻璃药丸**（默认贴左，点「⇄」滑到右侧，位置本地持久化）。玻璃体、双色折射光斑、循环流动高光、折射描边与悬浮阴影均为 `Canvas` 原生自绘，无第三方 UI 库。
- **底部 Dock**：首页 / 阵营 / 社区 / 团队 / 档案 5 项，手绘矢量图标、激活玻璃药丸、按压液态扩散与回弹动画。
- **在线直连**：默认加载 `https://xqly.xt91tv.shop`，登录、发帖等写操作完全可用。
- **离线镜像**：请求经 `WebViewClient.shouldInterceptRequest` 映射到内置资源；网络失败自动降级。包含 SPA history fallback、按扩展名的 MIME 判定、API JSON 快照回放。
- **全站档案**：内置可搜索的档案页，汇总所有页面正文、接口 JSON 快照与资源清单，可跳回对应页面。
- **其他**：外链走系统浏览器、视频全屏、返回键双击退出、加载进度条、启动闪屏。

## 内容规模

| 项目 | 数量 |
|---|---|
| 镜像静态资源 | 202 个文件 / 约 10.4 MB（主站 + `/wiki/` + `/admin/` 三个应用） |
| 页面正文 | 17 页 / 11,143 字（无头浏览器渲染提取） |
| 接口数据快照 | 16 组 JSON |
| APK 内置 assets | 219 个文件 |
| 已验证离线路由 | 16/17 完整渲染、0 资源缺失（后台页需登录，属正常） |

## 快速开始

### 环境要求

- JDK 8+（用于 `javac` / `keytool`）
- Android SDK：`platforms;android-35` 与 `build-tools;35.0.0`
- `zip`、`unzip`
- 复现爬取时另需 Python 3.9+、`requests`、`playwright`（含 Chromium）

```bash
sdkmanager "platforms;android-35" "build-tools;35.0.0"
export ANDROID_HOME=/path/to/android-sdk
```

### 构建

```bash
git clone <本仓库地址>
cd xqly-app
bash build.sh
```

脚本会依次完成资源编译 → 链接 → javac → d8 → 组装 → zipalign → v1/v2/v3 签名，产物默认输出到 `dist/星穹旅驿.apk`。

可覆盖的环境变量：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` | `/opt/android-sdk` | SDK 路径 |
| `ANDROID_BUILD_TOOLS` | `35.0.0` | build-tools 版本 |
| `OUT` | `dist/星穹旅驿.apk` | APK 输出路径 |
| `KS` | `build/xqly.jks` | 签名密钥路径，可指向**仓库外**的私有密钥 |
| `KS_PASS` / `KEY_PASS` | `xqly2026` | 调试密钥口令，**正式发布请换成私有密钥** |

首次构建会在 `build/` 下自动生成一把调试密钥。该目录已被 `.gitignore` 排除，**任何情况下都不要把 `.jks` 提交到公开仓库**。若要重建与 `dist/` 中产物签名一致的 APK（保证覆盖安装不冲突），请把私钥放在仓库外并用 `KS=/path/to/key.jks bash build.sh` 指定。

### 安装

```bash
adb install -r dist/星穹旅驿.apk
```

`minSdk 24`（Android 7.0+）。APK 为自签名，安装时系统提示「未知来源」属正常现象。

### 复现离线镜像

```bash
pip install requests playwright && playwright install chromium
python3 tools/mirror.py --all      # 重新抓取全站镜像
python3 tools/verify_offline.py    # 校验镜像能否完整渲染
python3 tools/redact.py            # 可选：预览敏感信息命中（默认不改文件）
python3 tools/redact.py --apply    # 确认后执行脱敏
```

`tools/mirror.py` 会重新抓取静态资源（含 Vite 懒加载 chunk）、渲染各路由提取正文、保存 API 快照，并用浏览器网络响应监听做兜底，输出到 `assets/`。

## 目录结构

```
├── AndroidManifest.xml
├── build.sh                  # 零 Gradle 构建脚本
├── src/cn/xqly/app/
│   ├── MainActivity.java     # WebView、侧边药丸、离线映射、状态管理
│   ├── GlassDrawable.java    # 液态玻璃绘制
│   └── GlassDock.java        # 底部 Dock
├── res/                      # 图标与样式
├── assets/
│   ├── site/                 # 全站离线镜像（第三方内容，见 NOTICE.md）
│   ├── data/                 # API JSON 快照
│   └── archive.html          # 内置全站档案页
├── tools/                    # 爬取、校验、脱敏脚本
├── docs/                     # 预览图、可浏览的全站档案
└── dist/星穹旅驿.apk         # 已签名产物
```

## ⚠️ 内容来源与版权

`assets/site/`、`assets/data/` 以及 `docs/全站内容档案.html` 中的网页文本、图片、字体、JS/CSS 与接口数据，**均归原站 xqly.xt91tv.shop 及其权利人所有**，本仓库仅作技术演示与离线存档，未获得授权，也不对其主张任何权利。

镜像数据中还包含从公开论坛运维公告里抓取到的服务器路径、备份文件名与部署命令等细节。**将本仓库设为公开，意味着这些内容会被一并公开传播**，请自行确认已取得必要授权。详见 [NOTICE.md](NOTICE.md)；如需降低风险可运行 `tools/redact.py`。

`LICENSE`（MIT）**仅适用于本仓库作者原创的代码**，不适用于上述第三方内容。

## 免责声明

本项目与「星穹旅驿」服务器运营方无关联，非官方客户端。作者不对使用本仓库内容所产生的版权、数据合规或其他法律后果承担责任。若您是权利人并希望移除相关内容，请提交 issue 联系。
