#!/bin/bash
# 给 release 未签名包补 debug 签名，并输出成 BaiAgent-1.2.0.apk（名字要和 get.php 直链一致）
cd /sdcard/AndroidCSProjects/BaiAgent || exit 9
APK=app/build/outputs/apk/release/app-release-unsigned.apk
OUT=app/build/outputs/apk/release/BaiAgent-1.2.0.apk
BT=$(ls -d "$ANDROID_HOME"/build-tools/*/ 2>/dev/null | sort -V | tail -1)
BT=${BT%/}
ZL="$BT/zipalign"
AS=$(command -v apksigner 2>/dev/null)
[ -n "$AS" ] || AS="$BT/apksigner"
[ -x "$AS" ] || { echo "找不到 apksigner"; exit 2; }
#zipalign 在这个 aarch64 环境是 x86 二进制，跑不了；对齐只是体积优化，缺了照样能装
if [ -x "$ZL" ] && "$ZL" -f -p 4 "$APK" /tmp/bai-aligned.apk 2>/dev/null; then
    SRC=/tmp/bai-aligned.apk
    echo "已 zipalign"
else
    SRC="$APK"
    echo "跳过 zipalign（当前环境跑不了，不影响安装）"
fi
"$AS" sign --ks "$HOME/.android/debug.keystore" \
    --ks-pass pass:android --key-pass pass:android \
    --ks-key-alias androiddebugkey \
    --out "$OUT" "$SRC" || { echo "签名失败"; exit 4; }
"$BT/aapt2" dump badging "$OUT" 2>/dev/null | head -2
ls -l "$OUT"
echo "SHA256: $(sha256sum "$OUT" | cut -d' ' -f1)"