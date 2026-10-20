#!/bin/bash
# 非交互构建 release（混淆包）：结果只写日志，供轮询读取
cd /sdcard/AndroidCSProjects/BaiAgent || exit 9
mkdir -p logs
bash app/build.sh -r < /dev/null > logs/ui-build-release.log 2>&1
echo "EXIT=$?" >> logs/ui-build-release.log
tail -5 logs/ui-build-release.log