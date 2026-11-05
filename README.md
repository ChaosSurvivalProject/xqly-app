# 星穹旅驿 · 官方 Android 客户端

**由星穹旅驿服务器运营团队官方发布**的 Android 客户端，官网：[xqly.xt91tv.shop](https://xqly.xt91tv.shop)。

原生液态玻璃界面、底部 Dock 导航，**在线直连与离线镜像两种模式均可完整使用**；内置「全站内容档案」与本地全文检索，离线时还有小游戏「方块合成」可以解乏；支持**平板 / 横屏 / 折叠屏自适应**，并内置**可信自动更新**。当前版本 **v2.0.0**（`versionCode=5`）。

> 📦 不想自己编译？直接到 [Releases 页面](https://github.com/ChaosSurvivalProject/xqly-app/releases) 下载已签名的 APK，安装即用。

![界面预览](docs/界面预览.png)

## v2.0.0 更新重点

这是一次**安全、设计系统与工程基线**的产品级升级，不是换皮：

- **可信更新链**：发布者钉扎 + Release 严格解析 + 下载主机白名单 + 整包 SHA-256 + **APK 签名证书指纹钉扎** + 版本 / versionCode 非降级；下载支持进度、速度、剩余时间、取消与断点续传；所有校验结果可在「更新中心」查看。校验失败只删临时文件，当前版本完好。
- **WebView 安全边界**：默认禁用文件访问、调试、混合内容、表单与密码保存；严格协议 / 主机 / 路径白名单；**唯一版本化 `postMessage` 桥**，每条消息校验来源、版本、类型、参数、权限、长度与幂等；外链走系统浏览器；加载超时、断网、证书错误都有明确状态与重试。
- **「星穹玻璃」设计系统**：`core/DesignTokens` 为唯一真源，深/浅主题 × 星穹玻璃 / 原生节能 / 增强可读三模式，共享同一套导航与业务状态；玻璃只用于 Dock、悬浮工具与弹层，正文用稳定实底色；统一光源、同心圆角、WCAG AA 对比度。
- **全状态 UI**：11 种状态（加载 / 成功 / 空 / 无网有缓存 / 无网无缓存 / 失败重试 / 部分可用 / 校验失败 / 权限拒绝 / 存储不足 / 待切换）覆盖到每个页面，不再有无限旋转加载。
- **本地全文检索**：CJK 单字 + bigram、标题/标签加权、`<mark>` 高亮与上下文预览、筛选、最近搜索；随包内置真实索引，断网可搜。
- **方块合成 2.0**：确定性规则引擎、3 次撤销、Hold/下一块预览、每日挑战（本地确定性种子）、成就、色盲友好形状、暂停/恢复、存档写入用户数据区。无广告、无付费、无联网。
- **数据隔离与迁移**：用户数据 `xqly_user` 与缓存 `xqly_cache` 分文件，schema 0→2 逐级迁移；**清理缓存不会删除收藏、历史、阅读进度或游戏存档**。
- **内容清单**：`assets/MANIFEST.json` 内置 227 个文件的 SHA-256 与体积，启动时抽样校验，防 ZIP 路径穿越、超大解压比、截断与版本回退。
- **工程基线**：新增 14 个零 Android 依赖的纯逻辑 `core` 类、123 个自动化测试（JVM 104 + Node 19）、可重复的 9 步构建链与回滚点 `pre-final-upgrade`。

历史版本：v1.3.0 重构了导航栏（仿 Apple macOS / iOS 26 液态玻璃：Dock 放大镜、选中磨砂玻璃药丸、顶部受光高光、侧边统一玻璃药丸、滚动收拢）；v1.2.0 加入平板适配与自动更新；v1.1.0 加入在线/离线双模式与离线小游戏。

> 完整改造前后对比、实测数据、未完成项与原因见 [验收报告](docs/ACCEPTANCE.md)。

## 特性

- **双模式均可使用**：在线模式直连官网，登录、发帖等写操作完全可用；离线模式走内置全站镜像，浏览正文、Wiki、社区帖子不受网络影响。切换即时生效，网络异常时自动降级。
- **离线镜像**：`WebViewClient.shouldInterceptRequest` 映射到内置资源，含 SPA history fallback、按扩展名 MIME 判定、API JSON 快照回放。
- **平板 / 大屏自适应**：按最短边计算缩放系数；横屏、平板与展开折叠屏自动切换为竖向 rail，正文限制最大宽度；手机保持底部 Dock。
- **液态玻璃界面**：侧边玻璃药丸（默认贴左，可换边并持久化）、`Canvas` 原生自绘玻璃体与折射光斑、底部 Dock 放大镜与激活玻璃药丸。
- **全站档案 / 资料检索**：内置可搜索档案页与资料页，汇总页面正文、接口 JSON 快照与资源清单，可跳回对应页面。
- **自动更新**：启动后延迟静默检查 GitHub Release，发现新版本提示下载安装；侧边药丸点「⇧ 更新」手动检查；**长按品牌（✦）打开设置**，可切换三种显示模式、深浅主题、阅读设置、功能开关、查看更新中心与隐私说明。
- **其他**：外链走系统浏览器、视频全屏、返回键双击退出、加载进度条、启动闪屏、最小触控目标与键盘操作支持。

![离线小游戏](docs/游戏预览.png)

![平板与手机布局对比](docs/平板预览.png)

## 内容规模

| 项目 | 数量 |
|---|---|
| 镜像静态资源 | 202 个文件 / 约 10.4 MB（主站 + `/wiki/` + `/admin/`） |
| 页面正文 | 17 页 / 11,143 字 |
| 接口数据快照 | 16 组 JSON |
| 内置内容清单 | 227 个文件 / 10,526,099 bytes |
| 本地检索索引 | 13 条真实文档（文章、路由、社区/知识库/监控快照、Wiki 目录） |
| APK 内文件总数 | 245 |
| Java 源码 | 25 个文件 / 4,941 行 |
| 自动化测试 | 123 个用例（JVM 104 + Node 19） |

## 快速开始

### 环境要求

- JDK 8+（`javac` / `keytool`）
- Android SDK：`platforms;android-35` 与 `build-tools;35.0.0`
- Python 3、Node.js、`zip`、`unzip`
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

`build.sh` 是可重复的 9 步构建链，任一环节失败即中止：

| 步骤 | 内容 |
|---|---|
| 0 | 校验 `AndroidManifest.xml`、`MainActivity`、`build.sh` 三处版本一致 |
| 1 | 跑 JVM core 逻辑测试（104 用例） |
| 2 | 跑 Node 游戏规则引擎测试（19 用例） |
| 3 | 生成并校验 `search-index.json` 与 `MANIFEST.json` |
| 4–7 | aapt2 编译/链接 → javac → d8 |
| 8 | 组装 APK、zipalign，并比对 APK 内清单与源码树字节一致 |
| 9 | 签名（v1/v2/v3）→ `apksigner verify` → 输出 SHA-256 与证书指纹 |

单独跑测试：

```bash
bash tests/run-tests.sh
node tests/game-engine.test.js
python3 tools/gen_manifest.py --check
python3 tools/gen_search_index.py --check
```

可覆盖的环境变量：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` | `/opt/android-sdk` | SDK 路径 |
| `ANDROID_BUILD_TOOLS` | `35.0.0` | build-tools 版本 |
| `OUT` | `dist/星穹旅驿.apk` | APK 输出路径 |
| `KS` | `~/.xqly-keys/xqly.jks`，否则 `build/xqly.jks` | 签名密钥，可指向仓库外私有密钥 |
| `KS_PASS` / `KEY_PASS` | `xqly2026` | 调试密钥口令，正式发布请用私有密钥 |

**正式发布务必使用仓库外私有密钥**；若脚本回退到调试密钥会打印警告，该产物无法覆盖升级。`.jks`、`build/`、`dist/` 均已被 `.gitignore` 排除，**任何情况下都不要把签名私钥提交到公开仓库**。

### 安装

**方式一（推荐）**：到 [Releases](https://github.com/ChaosSurvivalProject/xqly-app/releases) 下载 `xqly-app-v2.0.0.apk`，点击安装即可。应用内也支持自动更新。

**方式二**：本地编译后安装。

```bash
adb install -r dist/星穹旅驿.apk
```

`minSdk 24`（Android 7.0+）。APK 为自签名，安装时系统提示「未知来源」属正常现象；从 v1.1.0 起的所有版本使用同一签名证书，覆盖安装不会冲突。

### 复现离线镜像

```bash
pip install requests playwright && playwright install chromium
python3 tools/mirror.py --all      # 重新抓取全站镜像
python3 tools/verify_offline.py    # 校验镜像能否完整渲染
python3 tools/redact.py --apply    # 可选：脱敏后重新发布
python3 tools/gen_search_index.py  # 重建本地检索索引
python3 tools/gen_manifest.py      # 重建内容清单（会自动递增 version）
```

## 发布与更新

```bash
export GH_TOKEN=<你的 Personal Access Token>
bash scripts/publish.sh
cp "dist/星穹旅驿.apk" /tmp/xqly-app-v2.0.0.apk
gh release create v2.0.0 /tmp/xqly-app-v2.0.0.apk --repo ChaosSurvivalProject/xqly-app --title "v2.0.0"
```

令牌只在运行时从环境变量读取，不写入仓库或 git 配置；git 认证头只对 `github.com` 主机生效。完整的命名规则、发布后 SHA-256 校验、可信更新链与回滚步骤见 [发布指南](docs/RELEASE.md)。

## 文档

| 文档 | 内容 |
|---|---|
| [验收报告](docs/ACCEPTANCE.md) | 改造前后对比、实测结果、未完成项与真实原因、回滚计划 |
| [迁移映射](docs/MIGRATION.md) | Kotlin / Compose / Material 3 迁移蓝图与人工补齐清单 |
| [发布指南](docs/RELEASE.md) | 版本号、Release 命名、校验、更新链、回滚 |
| [隐私与数据清单](docs/PRIVACY.md) | 本机数据、网络请求、权限、日志、桥能力边界 |

> **关于 Kotlin / Compose 重写**：当前仓库仍为原生 Java + View + WebView 架构。完整迁移需要 Kotlin 编译器、AGP、Compose 依赖与真机测试环境；本次交付已把安全、设计系统、状态机、逻辑与测试做实，并给出逐文件迁移映射。详见 [验收报告第 5 节](docs/ACCEPTANCE.md) 与 [迁移映射](docs/MIGRATION.md)。

## 目录结构

```
├── AndroidManifest.xml
├── build.sh                     # 零 Gradle 可重复构建（9 步，含测试门禁）
├── src/cn/xqly/app/
│   ├── MainActivity.java        # 入口、WebView、浮层、离线映射、自适应、设置
│   ├── WebBridge.java           # 唯一版本化 postMessage 桥
│   ├── Updater.java             # 可信更新链
│   ├── UpdateCenter.java        # 更新中心（校验结果 / 记录 / 回退）
│   ├── StateOverlay.java        # 全状态 UI
│   ├── FeatureGate.java         # 功能开关 / Kill Switch
│   ├── PrefsStore.java          # 用户数据与缓存分文件
│   ├── GlassDrawable.java / GlassDock.java / XFileProvider.java
│   └── core/                    # 14 个零 Android 依赖的纯逻辑类
├── res/
├── assets/
│   ├── site/                    # 全站离线镜像
│   ├── data/                    # API JSON 快照 + search-index.json
│   ├── config/flags.json        # 功能开关
│   ├── MANIFEST.json            # 内置内容清单（SHA-256 / size）
│   ├── bridge-shim.js           # 页面侧版本化桥
│   ├── game-engine.js           # 方块合成规则引擎（纯逻辑）
│   ├── archive.html / library.html / game.html
│   └── library.css / game.css   # 星穹玻璃设计令牌
├── tests/                       # JVM 测试 + Node 测试 + 运行脚本
├── tools/                       # 爬取、校验、脱敏、索引与清单生成
├── docs/                        # 验收报告 / 迁移 / 发布 / 隐私 / 预览图
└── dist/星穹旅驿.apk            # 已签名产物（不入库）
```

## 版权与授权

本仓库由**星穹旅驿运营团队**发布。客户端自研代码（源码、构建脚本、工具脚本）以 [MIT](LICENSE) 开源；官网文字、图片、字体、商标及站点素材的版权归星穹旅驿运营团队所有，不在 MIT 授权范围内。详见 [NOTICE.md](NOTICE.md)。

镜像的论坛公告中含有服务器路径、备份文件名等运维细节。公开仓库会一并公开这些信息，运营团队如希望移除，可运行 `python3 tools/redact.py --apply` 批量脱敏后重新发布。

## 关于

这是星穹旅驿的**官方** Android 客户端。在线、离线两种模式均可完整使用；离线模式内置全站内容与小游戏，适合网络不佳或出行途中查阅资料。问题反馈与功能建议请到本仓库提交 issue。
