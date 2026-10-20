#!/bin/bash
# 非交互构建：结果只写日志，供轮询读取
cd /sdcard/AndroidCSProjects/BaiAgent || exit 9
bash app/build.sh -d < /dev/null > logs/ui-build.log 2>&1
echo "EXIT=$?" >> logs/ui-build.log
tail -3 logs/ui-build.log