#!/usr/bin/env bash
# 星穹旅驿 Android 客户端 —— 零 Gradle 构建脚本
# 只依赖 Android SDK build-tools 与 JDK，不需要 Android Studio / Gradle。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}"
BT_VER="${ANDROID_BUILD_TOOLS:-35.0.0}"
PLATFORM="${ANDROID_PLATFORM:-android-35}"
BT="$SDK/build-tools/$BT_VER"
JAR="$SDK/platforms/$PLATFORM/android.jar"

# 输出与签名配置（可用环境变量覆盖）
OUT="${OUT:-$ROOT/dist/星穹旅驿.apk}"
KS="${KS:-$ROOT/build/xqly.jks}"    # 可用 KS 指向仓库外的私有密钥
KS_PASS="${KS_PASS:-xqly2026}"      # 仅用于本地调试签名；正式发布请改用私有密钥
KEY_PASS="${KEY_PASS:-$KS_PASS}"

for f in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$JAR"; do
  [ -e "$f" ] || { echo "缺少依赖: $f"; echo "请先安装 SDK 组件：sdkmanager \"platforms;$PLATFORM\" \"build-tools;$BT_VER\""; exit 1; }
done

mkdir -p "$(dirname "$OUT")"
cd "$ROOT"
# 只清理中间产物，保留 build/xqly.jks，避免每次重建都换签名导致覆盖安装失败
mkdir -p build
rm -rf build/{res,gen,classes,dex,staging} build/*.apk build/res.zip build/*.txt
mkdir -p build/{res,gen,classes,dex,staging}

echo "=== [1/7] 编译资源 ==="
"$BT/aapt2" compile --dir res -o build/res.zip

echo "=== [2/7] 链接资源与 Manifest ==="
"$BT/aapt2" link -o build/base.apk \
  -I "$JAR" \
  --manifest AndroidManifest.xml \
  -R build/res.zip \
  --java build/gen \
  --min-sdk-version 24 --target-sdk-version 35 \
  --version-code 3 --version-name 1.2.0 \
  --auto-add-overlay

echo "=== [3/7] 编译 Java 源码 ==="
find build/gen src -name '*.java' > build/sources.txt
javac -source 8 -target 8 -encoding UTF-8 -nowarn \
  -cp "$JAR" -d build/classes @build/sources.txt

echo "=== [4/7] 转换为 DEX ==="
find build/classes -name '*.class' > build/classes.txt
"$BT/d8" --min-api 24 --lib "$JAR" --output build/dex @build/classes.txt

echo "=== [5/7] 组装 APK（dex + assets） ==="
cp build/base.apk build/unsigned.apk
cp build/dex/classes.dex build/staging/
cp -r assets build/staging/assets
( cd build/staging && zip -q -r ../unsigned.apk . )

echo "=== [6/7] 对齐 ==="
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk

echo "=== [7/7] 签名 ==="
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
"$BT/apksigner" verify --verbose "$OUT"

echo "=== 完成: $OUT ==="
ls -lh "$OUT"
