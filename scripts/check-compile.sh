#!/bin/bash
# 只做 Kotlin 编译检查（不出包、不联网拉依赖的增量编译）
cd /sdcard/AndroidCSProjects/BaiAgent || exit 9
mkdir -p logs
if command -v aapt2 >/dev/null 2>&1; then
    AAPT2_ARG="-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)"
else
    AAPT2_ARG=""
fi
sh ./gradlew :app:compileDebugKotlin --stacktrace $AAPT2_ARG > logs/check-compile.log 2>&1
echo "EXIT=$?" >> logs/check-compile.log
grep -E "^e: |BUILD SUCCESSFUL|BUILD FAILED" logs/check-compile.log | head -12