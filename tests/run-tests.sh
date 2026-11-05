#!/usr/bin/env bash
# 星穹旅驿 · core 逻辑 JVM 单元测试（零依赖：只需 JDK，无需 Android SDK / 网络 / Gradle）
# 用法：bash tests/run-tests.sh
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
OUT="${XQLY_TEST_OUT:-build/test-classes}"
rm -rf "$OUT" && mkdir -p "$OUT"

echo "=== 编译 core（纯 Java 8，不链接 android.jar）==="
find src/cn/xqly/app/core -name '*.java' > "$OUT/core.txt"
javac -source 8 -target 8 -encoding UTF-8 -nowarn -d "$OUT" @"$OUT/core.txt"

echo "=== 编译测试 ==="
find tests -name '*.java' > "$OUT/tests.txt"
javac -source 8 -target 8 -encoding UTF-8 -nowarn -cp "$OUT" -d "$OUT" @"$OUT/tests.txt"

echo "=== 运行测试 ==="
java -cp "$OUT" tests.CoreTestSuite
