#!/usr/bin/env bash
# Project:    Baicodex
# Author:     by 沐白/Baix
# License:    AGPL-3.0 (GNU Affero General Public License v3.0)
# Date:       2026-11-27 03:55:00
# Repository: https://github.com/mubai0122/Baicodex
# Website:    https://agent.ai.baiccb.cn/
#
# 一键出安装包：
#   主程序 → jlink 运行时 → jpackage app-image → zip
#   安装向导 → jlink 精简运行时 → jpackage app-image → zip
#   自解压头（Rust）→ 三段拼成单个 EXE
#
# 大件中间产物（runtime / app-image）直接写在系统临时目录的
# baicodex-build-<时间戳> 里，项目 build/ 只留最终 zip 与 stub——
# 反复构建不需要删目录，磁盘清理交给系统。
#
# 前置：JDK 21（含 jpackage/jlink）、Gradle、rustc、python。
# 用法：./build.sh          产物在 dist/Baicodex-Setup-0.1.0.exe
set -euo pipefail

cd "$(dirname "$0")"
HERE="$(pwd)"
JDK="${JDK:-/c/AndroidSdk/jdk-21}"
GRADLE="${GRADLE:-/c/AndroidSdk/gradle-9.0.0/bin/gradle}"
RUSTC="${RUSTC:-$HOME/.cargo/bin/rustc}"
VERSION="0.1.0"
WIN="$HERE/../windows"
B="$HERE/build"
TMPW="$(cygpath -m "${TMPDIR:-/tmp}")"
WORK="$TMPW/baicodex-build-$(date +%Y%m%d-%H%M%S)"

step() { printf '\n\033[1;34m[%s]\033[0m %s\n' "$1" "$2"; }

step 1/7 "编译主程序并收集依赖"
(cd "$WIN" && JAVA_HOME="$JDK" PATH="$JDK/bin:$PATH" "$GRADLE" collectAppJars --offline -q)

step 2/7 "jlink 主程序运行时（$WORK/runtime）"
mkdir -p "$WORK"
"$JDK/bin/jlink" \
  --add-modules java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.xml,jdk.unsupported,jdk.crypto.ec,jdk.zipfs \
  --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
  --output "$WORK/runtime"

step 3/7 "jpackage 主程序 app-image"
"$JDK/bin/jpackage" \
  --type app-image \
  --name Baicodex \
  --app-version "$VERSION" \
  --input "$WIN/build/pkg/input" \
  --main-jar baicodex-windows-"$VERSION".jar \
  --main-class b.ai.desktop.MainKt \
  --runtime-image "$WORK/runtime" \
  --dest "$WORK/app" \
  --vendor "Mubai/Baix" \
  --description "Baicodex Desktop" \
  --java-options "-Dfile.encoding=UTF-8"
python tools/pack.py zip "$WORK/app/Baicodex" "$B/app.zip"

step 4/7 "编译安装向导"
mkdir -p "$B/classes" "$B/setupinput"
"$JDK/bin/javac" -encoding UTF-8 -d "$B/classes" $(find setup/src -name '*.java')
"$JDK/bin/jar" --create --file "$B/setupinput/setup.jar" --main-class b.ai.setup.Setup -C "$B/classes" .

step 5/7 "jlink 安装向导运行时（Swing 版，体积小一半）"
"$JDK/bin/jlink" \
  --add-modules java.base,java.desktop,java.logging,java.prefs,java.xml,jdk.unsupported \
  --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
  --output "$WORK/runtime-setup"
"$JDK/bin/jpackage" \
  --type app-image \
  --name BaicodexSetup \
  --app-version "$VERSION" \
  --input "$B/setupinput" \
  --main-jar setup.jar \
  --main-class b.ai.setup.Setup \
  --runtime-image "$WORK/runtime-setup" \
  --dest "$WORK/setupapp" \
  --vendor "Mubai/Baix" \
  --description "Baicodex Setup" \
  --java-options "-Dfile.encoding=UTF-8"
python tools/pack.py zip "$WORK/setupapp/BaicodexSetup" "$B/installer.zip"

step 6/7 "编译自解压头"
"$RUSTC" stub/sfx_stub.rs -O \
  -C target-feature=+crt-static -C strip=symbols -C panic=abort -C opt-level=z \
  -o "$B/stub.exe"

step 7/7 "合成单文件安装包"
python tools/pack.py merge "$B/stub.exe" "$B/installer.zip" "$B/app.zip" "dist/Baicodex-Setup-$VERSION.exe"

printf '\n中间产物：%s（可整目录删除）\n' "$WORK"
printf '完成：dist/Baicodex-Setup-%s.exe\n' "$VERSION"
