#!/usr/bin/env bash
# 发布 / 更新 GitHub 仓库。凭证只在运行时从环境变量读取，
# 绝不写入仓库文件、git 配置或任何持久化位置。
#
#   export GH_TOKEN=<你的 Personal Access Token>
#   bash scripts/publish.sh
#
# 可覆盖：XQLY_REPO(默认 ChaosSurvivalProject/xqly-app)、
#         XQLY_VISIBILITY(默认 public)、GIT_NAME / GIT_EMAIL(提交身份)、
#         COMMIT_MSG(提交信息)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
REPO="${XQLY_REPO:-ChaosSurvivalProject/xqly-app}"
VIS="${XQLY_VISIBILITY:-public}"
ORG="${REPO%%/*}"

: "${GH_TOKEN:?请先执行: export GH_TOKEN=<你的 PAT>}"
export GH_TOKEN
command -v gh >/dev/null 2>&1 || { echo "缺少 gh CLI：https://cli.github.com"; exit 1; }

echo "=== 1/5 校验身份 ==="
gh api user --jq '"  已认证为: " + .login'

echo "=== 2/5 校验目标 $REPO ==="
ME="$(gh api user --jq '.login')"
if gh repo view "$REPO" >/dev/null 2>&1; then
  echo "  仓库已存在，将执行增量更新"
  EXISTS=1
else
  echo "  仓库不存在，将新建（$VIS）"
  EXISTS=0
  if [ "$ORG" != "$ME" ]; then
    gh api "orgs/$ORG" --jq '"  组织: " + .login' >/dev/null 2>&1 \
      || { echo "  ! 无法访问组织 $ORG：请确认组织存在且令牌已获 SSO 授权"; exit 1; }
  fi
fi

if [ ! -d .git ]; then
  git init -b main
fi

echo "=== 3/5 提交 ==="
git add -A
if ! git diff --cached --quiet; then
  git -c user.name="${GIT_NAME:-xqly-app}" \
      -c user.email="${GIT_EMAIL:-xqly-app@users.noreply.github.com}" \
      commit -m "${COMMIT_MSG:-chore: 更新星穹旅驿 Android 客户端内容}" \
    | sed 's/^/  /'
else
  echo "  无新改动，跳过提交"
fi

# 只在 github.com 这个主机上附加认证头：重定向到 objects.githubusercontent.com
# 等其它主机时不会携带，避免令牌外泄给第三方。
AUTH="$(printf 'x-access-token:%s' "$GH_TOKEN" | base64 | tr -d '\n')"
GIT_AUTH=(git -c "http.https://github.com/.extraheader=AUTHORIZATION: basic $AUTH")

echo "=== 4/5 配置远端 ==="
REMOTE="https://github.com/$REPO.git"
if git remote get-url origin >/dev/null 2>&1; then
  git remote set-url origin "$REMOTE"
else
  git remote add origin "$REMOTE"
fi

echo "=== 5/5 创建/推送 ==="
if [ "$EXISTS" -eq 0 ]; then
  gh repo create "$REPO" "--$VIS" \
    --description "星穹旅驿 Android 客户端：液态玻璃 UI + 全站离线镜像" \
    >/dev/null
  echo "  已创建 $REPO"
fi
"${GIT_AUTH[@]}" push -u origin HEAD:main 2>&1 | sed 's/^/  /'

echo "=== 完成: https://github.com/$REPO ==="
