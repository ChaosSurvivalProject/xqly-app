# 星穹旅驿 · 官方 Android 客户端

**由星穹旅驿服务器运营团队官方发布**的 Android 客户端，官网：[xqly.xt91tv.shop](https://xqly.xt91tv.shop)。

原生液态玻璃界面、底部 Dock 导航，**在线直连与离线镜像两种模式均可完整使用**；内置「全站内容档案」，离线时还有小游戏「方块合成」可以解乏；支持**平板等大屏自适应**，并内置**自动更新**（拉取 GitHub Release，发现新版本自动提示）。当前版本 **v1.3.0**。

> v1.3.0 重点重构了导航栏：仿 Apple macOS / iOS 26「液态玻璃」设计语言，底部 Dock 加入 **macOS 风格放大镜**（靠近指针的图标平滑放大并溢出相邻项）、选中项**内嵌磨砂玻璃药丸**、顶部受光高光与同心圆角；侧边药丸的 6 个工具按钮也统一成同一套玻璃药丸材质；并新增 **iOS 26 式滚动收拢**（向下滚 Dock 收拢、上滚展开）。

> 📦 不想自己编译？直接到 [Releases 页面](https://github.com/ChaosSurvivalProject/xqly-app/releases) 下载已签名的 APK，安装即用。

![界面预览](docs/界面预览.png)

## 特性

- **平板 / 大屏自适应**（v1.2.0 新增）：基于屏幕最短边计算缩放系数，侧边玻璃药丸、底部 Dock、启动卡片、图标与字体随屏幕放大；平板上 Dock 自动**居中限宽**（不再拉成细长横条），横竖屏切换均正常。手机上保持原有全宽 Dock。
- **液态玻璃界面**：可换边的**侧边玻璃药丸**（默认贴左，点「⇄」滑到右侧，位置本地持久化）。玻璃体、双色折射光斑、循环流动高光、折射描边与悬浮阴影均为 `Canvas` 原生自绘，无第三方 UI 库。
- **底部 Dock**：首页 / 阵营 / 社区 / 团队 / 档案 5 项，手绘矢量图标、激活玻璃药丸、按压液态扩散与回弹动画。
- **双模式均可使用**：在线模式直连 `https://xqly.xt91tv.shop`，登录、发帖等写操作完全可用；离线模式走内置全站镜像，浏览正文、Wiki、社区帖子不受网络影响。切换即时生效，网络异常时自动降级到离线镜像。
- **离线镜像**：请求经 `WebViewClient.shouldInterceptRequest` 映射到内置资源；包含 SPA history fallback、按扩展名的 MIME 判定、API JSON 快照回放。
- **离线小游戏「方块合成」**：侧边药囊点「◈ 游戏」即可进入。4×4 方块合成，滑动屏幕或方向键操作，支持撤销、最高分与进度本地保存。纯本地单文件 HTML，无网络请求、无外部依赖，**飞行模式下照样能玩**，在线模式也可随时打开。
- **自动更新**（v1.2.0 新增）：启动后静默检查 GitHub Release 最新版本，发现新版本弹窗提示「下载并安装」；侧边药丸点「⇧ 更新」可手动检查，**长按品牌（✦）打开设置**可开关「自动检查更新」。下载通过私有 `FileProvider` 拉起系统安装，兼容 Android 8+ 的「安装未知应用」授权。
- **全站档案**：内置可搜索的档案页，汇总所有页面正文、接口 JSON 快照与资源清单，可跳回对应页面。
- **其他**：外链走系统浏览器、视频全屏、返回键双击退出、加载进度条、启动闪屏。

![离线小游戏](docs/游戏预览.png)

![平板与手机布局对比](docs/平板预览.png)

## 内容规模

| 项目 | 数量 |
|---|---|
| 镜像静态资源 | 202 个文件 / 约 10.4 MB（主站 + `/wiki/` + `/admin/` 三个应用） |
| 页面正文 | 17 页 / 11,143 字（无头浏览器渲染提取） |
| 接口数据快照 | 16 组 JSON |
| APK 内置 assets | 220 个文件（含 `game.html`） |
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

**方式一（推荐）**：到 [Releases](https://github.com/ChaosSurvivalProject/xqly-app/releases) 下载 `xqly-app-v1.2.0.apk`，点击安装即可，无需编译环境。应用内也支持自动更新（启动检查 GitHub Release，发现新版本自动提示下载安装）。

**方式二**：本地编译后安装。

```bash
adb install -r dist/星穹旅驿.apk
```

`minSdk 24`（Android 7.0+）。APK 为自签名，安装时系统提示「未知来源」属正常现象；从旧版覆盖安装不会冲突（签名证书一致）。

### 复现离线镜像

```bash
pip install requests playwright && playwright install chromium
python3 tools/mirror.py --all      # 重新抓取全站镜像
python3 tools/verify_offline.py    # 校验镜像能否完整渲染
python3 tools/redact.py            # 可选：预览敏感信息命中（默认不改文件）
python3 tools/redact.py --apply    # 确认后执行脱敏
```

`tools/mirror.py` 会重新抓取静态资源（含 Vite 懒加载 chunk）、渲染各路由提取正文、保存 API 快照，并用浏览器网络响应监听做兜底，输出到 `assets/`。

## 发布与更新

发布通过 `scripts/publish.sh` 完成，脚本**幂等，可反复运行**：首次创建仓库，之后推送增量更新。

```bash
export GH_TOKEN=<你的 Personal Access Token>
bash scripts/publish.sh

# 自定义提交信息
COMMIT_MSG="docs: 更新说明文档" bash scripts/publish.sh
```

| 变量 | 默认值 | 说明 |
|---|---|---|
| `XQLY_REPO` | `ChaosSurvivalProject/xqly-app` | 目标仓库 |
| `XQLY_VISIBILITY` | `public` | 新建仓库可见性 |
| `COMMIT_MSG` | `chore: 更新星穹旅驿 Android 客户端内容` | 提交信息 |
| `GIT_NAME` / `GIT_EMAIL` | `xqly-app` | 提交身份 |

安全设计：令牌**只在运行时从环境变量读取**，不写入仓库文件、`.git/config` 或任何持久化位置；git 认证头只对 `github.com` 主机生效，重定向到 `objects.githubusercontent.com` 等其它主机时不会携带。

若 `git push` 因网络抖动超时，重跑一次即可；国内网络可先运行 `github-accelerator` 的 `check` / `apply` 修复 hosts。

## 目录结构

```
├── AndroidManifest.xml
├── build.sh                  # 零 Gradle 构建脚本
├── src/cn/xqly/app/
│   ├── MainActivity.java     # WebView、侧边药丸、离线映射、游戏浮层、平板缩放、更新设置
│   ├── GlassDrawable.java    # 液态玻璃绘制
│   ├── GlassDock.java        # 底部 Dock（支持缩放）
│   ├── Updater.java          # GitHub Release 自动更新（检测/下载/安装）
│   └── XFileProvider.java    # 私有 FileProvider，供安装器读取 APK
├── res/                      # 图标与样式
├── assets/
│   ├── site/                 # 全站离线镜像
│   ├── data/                 # API JSON 快照
│   ├── archive.html          # 内置全站档案页
│   └── game.html             # 离线小游戏「方块合成」（无网络依赖）
├── tools/                    # 爬取、校验、脱敏脚本
├── docs/                     # 预览图、可浏览的全站档案
└── dist/星穹旅驿.apk         # 已签名产物
```

## 版权与授权

本仓库由**星穹旅驿运营团队**发布。客户端自研代码（源码、构建脚本、工具脚本）以 [MIT](LICENSE) 开源；官网文字、图片、字体、商标及站点素材的版权归星穹旅驿运营团队所有，不在 MIT 授权范围内。详见 [NOTICE.md](NOTICE.md)。

镜像的论坛公告中含有服务器路径、备份文件名等运维细节。公开仓库会一并公开这些信息，运营团队如希望移除，可运行 `python3 tools/redact.py --apply` 批量脱敏后重新发布。

## 关于

这是星穹旅驿的**官方** Android 客户端。在线、离线两种模式均可完整使用；离线模式内置全站内容与小游戏，适合网络不佳或出行途中查阅资料。问题反馈与功能建议请到本仓库提交 issue。
