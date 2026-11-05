#!/usr/bin/env bash
# 星穹旅驿 Android 客户端 —— 零 Gradle 可重复构建脚本
# 只依赖 Android SDK build-tools、JDK、Dart SDK、Python3、Node；不需要 Android Studio / Gradle / 网络。
#
# 一次命令完成：版本一致性 → Dart 静态分析与令牌生成 → Dart/JVM/Node 三套测试
#              → 生成索引与内容清单 → 编译 → 签名 → APK 内资源字节比对。
# 正式发布必须提供仓库外的私有签名密钥，否则会用调试密钥（无法覆盖升级）并给出警告。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}"
BT_VER="${ANDROID_BUILD_TOOLS:-35.0.0}"
PLATFORM="${ANDROID_PLATFORM:-android-35}"
BT="$SDK/build-tools/$BT_VER"
JAR="$SDK/platforms/$PLATFORM/android.jar"

# 版本唯一真源：Manifest 与 MainActivity 必须与此一致，防止漂移
V_CODE=6
V_NAME=2.0.1

OUT="${OUT:-$ROOT/dist/星穹旅驿.apk}"
KS_PASS="${KS_PASS:-xqly2026}"
KEY_PASS="${KEY_PASS:-$KS_PASS}"
DART="${DART:-$(command -v dart || true)}"

echo "=== [0/12] 版本一致性与工具链检查 ==="
MV_CODE=$(grep -o 'android:versionCode="[0-9]*"' AndroidManifest.xml | grep -o '[0-9]*')
MV_NAME=$(grep -o 'android:versionName="[^"]*"' AndroidManifest.xml | cut -d'"' -f2)
JAVA_NAME=$(grep -o 'VERSION_NAME = "[^"]*"' src/cn/xqly/app/MainActivity.java | cut -d'"' -f2)
JAVA_CODE=$(grep -o 'VERSION_CODE = [0-9]*' src/cn/xqly/app/MainActivity.java | awk '{print $3}')
DART_NAME=$(grep -o "versionName = '[^']*'" shared/lib/src/identity.dart | cut -d"'" -f2)
DART_CODE=$(grep -o 'versionCode = [0-9]*' shared/lib/src/identity.dart | awk '{print $3}')
if [ "$MV_CODE" != "$V_CODE" ] || [ "$MV_NAME" != "$V_NAME" ] || \
   [ "$JAVA_CODE" != "$V_CODE" ] || [ "$JAVA_NAME" != "$V_NAME" ] || \
   [ "$DART_CODE" != "$V_CODE" ] || [ "$DART_NAME" != "$V_NAME" ]; then
  echo "版本号不一致：manifest=$MV_CODE/$MV_NAME java=$JAVA_CODE/$JAVA_NAME dart=$DART_CODE/$DART_NAME 期望=$V_CODE/$V_NAME" >&2
  exit 1
fi
for f in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$JAR"; do
  [ -e "$f" ] || { echo "缺少依赖: $f"; echo "请安装：sdkmanager \"platforms;$PLATFORM\" \"build-tools;$BT_VER\""; exit 1; }
done
command -v python3 >/dev/null || { echo "缺少 python3"; exit 1; }
command -v node >/dev/null || { echo "缺少 node"; exit 1; }
[ -n "$DART" ] && [ -x "$DART" ] || { echo "缺少 dart（共享核心门禁必需，可设置 DART=...）" >&2; exit 1; }
echo "  v$V_NAME (code $V_CODE) · $(basename "$BT") · dart=$(basename "$DART")"

cd "$ROOT"
mkdir -p "$(dirname "$OUT")" build
# 只清理中间产物，保留 build/xqly.jks 与 build/test-classes
rm -rf build/{res,gen,classes,dex,staging} build/*.apk build/res.zip build/*.txt build/apk-manifest.json build/apk-tokens.css
mkdir -p build/{res,gen,classes,dex,staging}

# Dart 包缓存放在 build/ 内（已被 .gitignore 排除），不污染源码树
export PUB_CACHE="${PUB_CACHE:-$ROOT/build/pub-cache}"

echo "=== [1/12] Dart 静态分析与设计令牌生成 ==="
( cd "$ROOT/shared" && "$DART" pub get --offline >/dev/null && "$DART" analyze )
( cd "$ROOT/shared" && "$DART" tool/gen_tokens.dart .. && "$DART" tool/gen_tokens.dart .. --check )

echo "=== [2/12] 生成并校验随包资源（本地索引 / 内容清单）==="
# 必须先于所有测试：索引与清单是随包资源的一部分，测试会读取并抽样校验它们。
# 若排在测试之后，源码改动会让旧清单的哈希断言失败（门禁曾真实捕获此问题）。
python3 tools/gen_search_index.py
python3 tools/gen_manifest.py
python3 tools/gen_search_index.py --check
python3 tools/gen_manifest.py --check

echo "=== [3/12] Dart 共享业务核心测试（152 用例）==="
( cd "$ROOT/shared" && "$DART" test/run_tests.dart .. | tail -4 )

echo "=== [4/12] JVM 逻辑测试（core，零依赖）==="
bash tests/run-tests.sh | tail -4

echo "=== [5/12] Node 游戏规则引擎测试 ==="
node tests/game-engine.test.js | tail -4

echo "=== [6/12] 编译资源 ==="
"$BT/aapt2" compile --dir res -o build/res.zip

echo "=== [7/12] 链接资源与 Manifest ==="
"$BT/aapt2" link -o build/base.apk \
  -I "$JAR" \
  --manifest AndroidManifest.xml \
  -R build/res.zip \
  --java build/gen \
  --min-sdk-version 24 --target-sdk-version 35 \
  --version-code "$V_CODE" --version-name "$V_NAME" \
  --auto-add-overlay

echo "=== [8/12] 编译 Java 源码 ==="
find build/gen src -name '*.java' > build/sources.txt
javac -source 8 -target 8 -encoding UTF-8 -nowarn \
  -cp "$JAR" -d build/classes @build/sources.txt

echo "=== [9/12] 转换为 DEX ==="
find build/classes -name '*.class' > build/classes.txt
"$BT/d8" --min-api 24 --lib "$JAR" --output build/dex @build/classes.txt

echo "=== [10/12] 组装 APK（dex + assets）并对齐 ==="
cp build/base.apk build/unsigned.apk
cp build/dex/classes.dex build/staging/
cp -r assets build/staging/assets
( cd build/staging && zip -q -r ../unsigned.apk . )
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk

echo "=== [11/12] 校验 APK 内随包资源字节一致 ==="
# 清单本身必须与源码树一致（防止打包阶段被替换）
unzip -p build/aligned.apk assets/MANIFEST.json > build/apk-manifest.json
cmp -s assets/MANIFEST.json build/apk-manifest.json || {
  echo "APK 内 MANIFEST.json 与源码不一致" >&2; exit 1; }
# 双平台共享设计令牌必须与源码树一致
unzip -p build/aligned.apk assets/design-tokens.css > build/apk-tokens.css
cmp -s assets/design-tokens.css build/apk-tokens.css || {
  echo "APK 内 design-tokens.css 与源码不一致" >&2; exit 1; }
echo "  MANIFEST.json / design-tokens.css 字节一致"

echo "=== [12/12] 签名与产物校验 ==="
if [ -z "${KS:-}" ]; then
  if [ -f "$HOME/.xqly-keys/xqly.jks" ]; then
    KS="$HOME/.xqly-keys/xqly.jks"
    echo "  使用仓库外发布密钥: $KS"
  else
    KS="$ROOT/build/xqly.jks"
    echo "  警告：未找到发布密钥，使用调试密钥，产物不可用于覆盖升级" >&2
  fi
fi
if [ ! -f "$KS" ]; then
  keytool -genkeypair -v -keystore "$KS" -alias xqly \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass "$KS_PASS" -keypass "$KEY_PASS" \
    -dname "CN=XQLY, OU=App, O=XingQiongLuYi, L=Shenzhen, ST=Guangdong, C=CN" > /dev/null 2>&1
  echo "  已生成调试密钥（未纳入版本控制）: $KS"
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KEY_PASS" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT" build/aligned.apk
"$BT/apksigner" verify --verbose "$OUT" > /dev/null

echo "=== 完成: $OUT ==="
ls -lh "$OUT"
echo "SHA-256: $(sha256sum "$OUT" | awk '{print $1}')"
echo "签名证书 SHA-256:"
"$BT/apksigner" verify --print-certs "$OUT" | grep -i 'SHA-256' | head -1 || true
