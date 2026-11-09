#!/usr/bin/env bash
# 终极防注水统计
set -uo pipefail
ROOT="${1:-.}"

echo "🔍 正在扫描纯净业务代码，排除所有第三方库和构建缓存..."

# 核心业务目录（只查他手写的）
TARGET_DIRS=("kotlin" "cpp/core" "cpp/ffi" "cpp/jni" "cpp/net" "cpp/tools" "rust/src" "rust/baiguard/src" "rust/crates" "assets/scripts" "assets/skills")

total_lines=0

for dir in "${TARGET_DIRS[@]}"; do
    if [ -d "$ROOT/$dir" ]; then
        # 排除所有自动生成的文件和第三方依赖
        lines=$(find "$ROOT/$dir" -type f \( -name "*.kt" -o -name "*.java" -o -name "*.cpp" -o -name "*.h" -o -name "*.rs" -o -name "*.sh" -o -name "*.mjs" -o -name "*.py" \) \
            ! -path "*/node_modules/*" ! -path "*/target/*" ! -path "*/_third_party/*" ! -path "*/build/*" ! -name "*.d.mts" \
            -exec wc -l {} + 2>/dev/null | tail -n 1 | awk '{print $1}')
        
        if [ -z "$lines" ]; then lines=0; fi
        echo "📁 $dir: $lines 行"
        total_lines=$((total_lines + lines))
    fi
done

echo "----------------------------------------"
echo "🎯 纯手工核心业务代码总行数: $total_lines"