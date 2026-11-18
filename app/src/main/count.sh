#!/usr/bin/env bash
# 统计「纯手工业务代码」行数：排除三方库、构建缓存
# Rust 内核在 rust/ 下摊平成单个 crate（无嵌套子层）
set -uo pipefail
ROOT="${1:-.}"
echo "正在扫描纯净业务代码，排除所有第三方库和构建缓存..."

#Android 工程内目录（相对 app/src/main/）
TARGET_DIRS=("kotlin" "cpp/core" "cpp/ffi" "cpp/jni" "cpp/net" "cpp/tools" "rust/src" "assets/scripts" "assets/skills")

total_lines=0

# 单目录统计：$1=基准目录 $2=相对子目录 $3=显示名
count_dir() {
    local base="$1" dir="$2" label="$3"
    [ -d "$base/$dir" ] || return 0
    local lines
    lines=$(find "$base/$dir" -type f \( -name "*.kt" -o -name "*.java" -o -name "*.cpp" -o -name "*.h" -o -name "*.rs" -o -name "*.sh" -o -name "*.mjs" -o -name "*.py" \) \
        ! -path "*/node_modules/*" ! -path "*/target/*" ! -path "*/_third_party/*" ! -path "*/build/*" ! -name "*.d.mts" \
        -exec wc -l {} + 2>/dev/null | tail -n 1 | awk '{print $1}')
    [ -z "$lines" ] && lines=0
    echo "📁 $label: $lines 行"
    total_lines=$((total_lines + lines))
} //单目录统计

for dir in "${TARGET_DIRS[@]}"; do
    count_dir "$ROOT" "$dir" "$dir"
done

echo "----------------------------------------"
echo "纯手工核心业务代码总行数: $total_lines"