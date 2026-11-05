# 发布、更新与回滚指南

## 1. 发布前检查

```bash
bash build.sh                 # 9 步：版本一致性 / 两套测试 / 资源生成 / 编译 / 打包 / 签名 / 校验
python3 tools/gen_manifest.py --check
python3 tools/gen_search_index.py --check
git status --short             # 确认没有 *.jks / *.apk / .env / Token 文件
```

`build.sh` 会强制校验 `AndroidManifest.xml`、`MainActivity.VERSION_*` 与脚本内 `V_CODE/V_NAME` 三者一致；不一致直接退出。

**签名密钥**：正式发布必须提供仓库外的私有密钥，否则脚本会退化到调试密钥并打印警告（该产物无法覆盖升级）。

```bash
# 推荐：放到 HOME 下的私有目录（.gitignore 覆盖不到，但也不在仓库内）
mkdir -p ~/.xqly-keys && chmod 700 ~/.xqly-keys
KS=/path/to/private/xqly.jks KS_PASS=<口令> KEY_PASS=<口令> bash build.sh
```

发布者身份由证书决定：**v1.1.0 起所有版本使用同一证书**，指纹
`e1e8edc56e9700a27ab2cd4ba5f45fa0ba1bd9a88e4a261de7ef3684b3c21c05`。
换证书 = 换发布者，客户端会因签名钉扎拒装且用户无法覆盖升级。

## 2. 版本号规则

| 字段 | 规则 |
|---|---|
| `versionName` | 语义化 `x.y.z` |
| `versionCode` | **严格递增整数**；`VersionPolicy.decide` 会拒绝降级 |
| Release tag | `v<versionName>`，例如 `v2.0.0` |
| 资产名 | `xqly-app-v<versionName>.apk`，例如 `xqly-app-v2.0.0.apk` |
| Release 状态 | `draft=false`、`prerelease=false` |

`UpdateVerdict.validAssetName` 要求资产名前缀 `xqly-app-v`、后缀 `.apk`，中间版本与 tag 相等；`parseRelease` 还要求 `html_url` 指向 `ChaosSurvivalProject/xqly-app/releases/tag/`，下载 URL 主机在 `DOWNLOAD_HOSTS`（`github.com` / `objects.githubusercontent.com` / `release-assets.githubusercontent.com`）白名单内。任一不符，客户端直接拒绝，不会仅因为「URL 来自 GitHub」就信任。

## 3. 发布命令

```bash
# 3.1 推送源码（令牌只从环境变量读取，不写入仓库）
export GH_TOKEN=<PAT>
bash scripts/publish.sh

# 3.2 上传已签名 APK，资产名必须是 ASCII 且与 tag 一致
cp "dist/星穹旅驿.apk" /tmp/xqly-app-v2.0.0.apk
gh release create v2.0.0 /tmp/xqly-app-v2.0.0.apk \
  --repo ChaosSurvivalProject/xqly-app \
  --title "v2.0.0" \
  --notes "见 docs/ACCEPTANCE.md" \
  --verify-tag
```

## 4. 发布后校验（必做）

```bash
# 资产摘要必须与本地产物 SHA-256 完全一致
gh release view v2.0.0 --repo ChaosSurvivalProject/xqly-app --json tagName,assets
sha256sum "dist/星穹旅驿.apk"
```

核对项：

- `digest` = `sha256:<本地 SHA-256>`
- `size` = 本地字节数
- `state` = `uploaded`，非 draft / 非 prerelease
- `browser_download_url` 主机在白名单内

GitHub 会自动为 Release 资产生成 `digest`（`sha256:` 前缀），客户端 `UpdateVerdict` 会比对下载后的整包实测 SHA-256 与该声明值。

## 5. 客户端可信更新链

```
首屏稳定后（延迟 6s，受 auto_update 开关与网络状态控制）
  → GET https://api.github.com/repos/ChaosSurvivalProject/xqly-app/releases/latest
  → parseRelease：发布者 / tag / 资产名 / 下载主机 / 大小 / 声明摘要
  → 下载（白名单重定向、Range 断点续传、进度/速度/ETA、可取消）
  → 实测：整包 SHA-256 / APK 签名证书指纹 / versionName / versionCode
  → UpdateVerdict.evaluate 全项通过才安装；否则只删临时文件
  → 更新中心记录校验摘要（URL 与长文本脱敏）
```

系统限制导致无法直接安装（Android 8+ 未知来源授权）时，只做一次性引导并保留已校验的更新包，不反复弹窗、不显示虚假进度。

## 6. 回滚

| 场景 | 操作 |
|---|---|
| 代码回退 | `git checkout pre-final-upgrade`，或 `git revert <v2.0.0 提交>` |
| Release 回退 | 保留 v1.3.0 Release 不动，删除/隐藏 v2.0.0 Release；用户手动安装旧版 |
| 客户端内容校验失败 | 自动保留当前可用版本，不写入 `pack.version`，不删除用户数据 |
| 更新包校验失败 | 只删除 `update.tmp.*` 临时文件，当前 APK 完好 |
| 线上事故 | 把 `assets/config/flags.json` 对应能力置 `false`（Kill Switch），或在设置里逐项关闭 |

> 注意：自动更新链**按设计拒绝降级安装**。因此从 v2.0.0 回退到 v1.3.0 需要用户手动安装，这是防止攻击者推送旧版本绕过修复的预期行为，不是缺陷。

## 7. 安全红线

- 绝不提交 `*.jks`、`*.keystore`、`.env`、PAT 或密码到仓库。
- 绝不更换签名证书。
- 绝不发布 `draft` / `prerelease` 作为 latest。
- 绝不上传未通过 `bash build.sh` 全部门禁的 APK。
- 发布后必须完成第 4 节的摘要比对，才算发布完成。
