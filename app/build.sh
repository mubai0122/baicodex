#!/bin/bash
# TextureBackend 单项目构建菜单

ROOT="/storage/emulated/0/AndroidCSProjects/BaiAgent/"
cd "$ROOT" || exit 1

# 标准 Android 单项目输出路径
APK_DEBUG="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
APK_RELEASE="$ROOT/app/build/outputs/apk/release/app-release-unsigned.apk"
# 如需改包名/启动 Activity，改这里
PKG="b.ai"
ACTIVITY=".MainActivity"

# 构建用 gradle：必须走 wrapper（系统 gradle 9.6 与 AGP 8.13 不兼容）
GRADLE="sh ./gradlew"
# aarch64 环境没有 x86 版 aapt2，用本机 aapt2 覆盖
AAPT2_ARG=""
if command -v aapt2 >/dev/null 2>&1; then
    AAPT2_ARG="-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)"
fi


install_apk() {
    local apk="$1"
    local name="$2"

    if [ ! -f "$apk" ]; then
        echo "❌ $name APK 不存在: $apk"
        return 1
    fi
    echo "安装 $name ..."

    # release 未签名 APK 先补 debug 签名
    if [[ "$apk" == *"-unsigned.apk" ]]; then
        local signed="${apk%-unsigned.apk}-signed.apk"
        local bt="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -1)"
        local zipalign="$bt/zipalign"
        local apksigner="$bt/apksigner"
        if [ ! -x "$apksigner" ]; then
            echo "⚠️ 找不到 apksigner，跳过签名直接安装（大概率失败）"
            signed="$apk"
        else
            "$zipalign" -f -p 4 "$apk" "${apk%.apk}-aligned.apk" && \
            "$apksigner" sign --ks "$HOME/.android/debug.keystore" \
                --ks-pass pass:android --key-pass pass:android \
                --ks-key-alias androiddebugkey \
                --out "$signed" "${apk%.apk}-aligned.apk" && \
            echo "✅ 已用 debug 签名: $signed" || { echo "❌ 签名失败"; return 1; }
        fi
        apk="$signed"
    fi

    su -c "cp '$apk' /data/local/tmp/tmp-install.apk && pm install -r /data/local/tmp/tmp-install.apk && rm -f /data/local/tmp/tmp-install.apk" 2>&1

    if [ -n "$PKG" ] && [ -n "$ACTIVITY" ]; then
        echo "启动 $name ..."
        su -c "am start -n $PKG/$ACTIVITY" 2>&1
    fi
}

run_build() {
    local TASK="$1"
    local NAME="$2"
    local IS_BUILD="$3"
    local APK_PATH="$4"

    TS=$(date +%Y%m%d-%H%M%S)
    LOG="${ROOT}/logs/${NAME}-${TS}.log"
    mkdir -p "${ROOT}/logs"

    clear
    echo "执行: gradle $TASK"
    echo "日志: $LOG"
    echo "--------------------------------------"
    echo ""
    echo ""
    for r in $(seq 1 10); do echo ""; done

    $GRADLE $TASK --stacktrace $AAPT2_ARG > "$LOG" 2>&1 &
    PID=$!
    SECONDS=0

    while kill -0 $PID 2>/dev/null; do
        printf "\033[4;1H\033[K⏳ 编译中... %d 秒" $SECONDS
        mapfile -t TASKS < <(grep "^> Task" "$LOG" 2>/dev/null | tail -n 10)
        for i in 0 1 2 3 4 5 6 7 8 9; do
            ROW=$((6 + i))
            LINE="${TASKS[$i]:-}"
            printf "\033[%d;1H\033[K%s" $ROW "$LINE"
        done
        printf "\033[4;25H"
        sleep 0.5
    done
    wait $PID
    printf "\033[17;1H\033[K"
    echo

    if grep -q "BUILD SUCCESSFUL" "$LOG"; then
        echo "✅ BUILD SUCCESSFUL"
        grep -E "^BUILD SUCCESSFUL" "$LOG" | tail -1

        if [ "$IS_BUILD" = "1" ] && [ -n "$APK_PATH" ]; then
            echo
            echo "APK 输出："
            if [ -f "$APK_PATH" ]; then
                echo "  $APK_PATH"
            else
                echo "  （未找到 $APK_PATH）"
            fi

            echo
            read -p "要安装吗？(y/N): " doinstall
            if [[ "$doinstall" == "y" || "$doinstall" == "Y" ]]; then
                echo
                install_apk "$APK_PATH" "$NAME"
            else
                echo "已跳过安装"
            fi
        fi
    elif grep -q "BUILD FAILED" "$LOG"; then
        echo "❌ BUILD FAILED"
        echo
        ERR_LINES=$(grep -nE "^e: |error:|FAILURE|Caused by|What went wrong|Unresolved reference" "$LOG" | head -n 20)
        echo "关键错误："
        echo "$ERR_LINES"
        echo
        COPY_TEXT="构建失败 - $NAME
日志: $LOG

关键错误:
$ERR_LINES"
        ERR_FILE="${ROOT}/logs/last-error.txt"
        echo "$COPY_TEXT" > "$ERR_FILE"
        if command -v termuxc >/dev/null 2>&1; then
            echo "$COPY_TEXT" | timeout 5 termuxc
            if [ $? -eq 0 ]; then
                echo "✅ 错误已复制到剪贴板"
            else
                echo "⚠️ 剪贴板失败，错误已写入: $ERR_FILE"
            fi
        else
            echo "⚠️ termuxc 不可用，错误已写入: $ERR_FILE"
        fi
    else
        echo "⚠️ 无法判断结果，请查看日志"
        tail -n 30 "$LOG"
    fi

    echo
    echo "完整日志: $LOG"
    echo
    read -p "按回车返回菜单..." _
}

# ===================== 启动参数：debug / release =====================
if [ $# -gt 0 ]; then
    case "$1" in
        -d|debug)
            run_build ":app:assembleDebug" "app-debug" 1 "$APK_DEBUG"
            exit 0
            ;;
        -r|release)
            run_build ":app:assembleRelease" "app-release" 1 "$APK_RELEASE"
            exit 0
            ;;
        *)
            echo "用法: $0 [debug|-d|release|-r]"
            exit 1
            ;;
    esac
fi

# ===================== 交互菜单 =====================
while true; do
    clear
    echo "======================================"
    echo "   TextureBackend 构建菜单"
    echo "======================================"
    echo "   1. 检查编译        (compileDebugKotlin)"
    echo "   2. 构建 debug      (assembleDebug + 询问安装)"
    echo "   3. 构建 release    (assembleRelease 混淆 + 询问安装)"
    echo "   4. 清空日志目录"
    echo "   0. 退出"
    echo "======================================"
    read -p "选择 [0-4] : " choice

    case "$choice" in
        1) TASK=":app:compileDebugKotlin"; NAME="app-check";   IS_BUILD=0; APK_PATH="";;
        2) TASK=":app:assembleDebug";      NAME="app-debug";   IS_BUILD=1; APK_PATH="$APK_DEBUG";;
        3) TASK=":app:assembleRelease";    NAME="app-release"; IS_BUILD=1; APK_PATH="$APK_RELEASE";;
        4)
            clear
            LOGDIR="${ROOT}/logs"
            if [ -d "$LOGDIR" ]; then
                COUNT=$(ls -1 "$LOGDIR" 2>/dev/null | wc -l)
                echo "日志目录: $LOGDIR"
                echo "共 $COUNT 个文件"
                echo
                read -p "确认清空？(y/N): " confirm
                if [[ "$confirm" == "y" || "$confirm" == "Y" ]]; then
                    rm -rf "${LOGDIR:?}"/*
                    echo "✅ 已清空"
                else
                    echo "已取消"
                fi
            else
                echo "日志目录不存在"
            fi
            echo
            read -p "按回车返回菜单..." _
            continue
            ;;
        0) echo "退出"; exit 0;;
        *) echo "无效选项"; sleep 1; continue;;
    esac

    run_build "$TASK" "$NAME" "$IS_BUILD" "$APK_PATH"
done