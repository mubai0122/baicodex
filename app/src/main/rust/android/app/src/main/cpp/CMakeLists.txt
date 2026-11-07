# CMakeLists.txt —— Android 侧原生构建。
#
# ============================================================================
# 先说结论
# ============================================================================
#
# 正常情况下**这个文件不需要参与构建**。
#
# 逻辑层全部在 Rust 里，产物是 `libbaicodex.so`（crate-type = cdylib），
# 由 cargo-ndk 产出后直接放进 `src/main/jniLibs/<abi>/`，Kotlin 侧
# `System.loadLibrary("baicodex")` 即可，中间不需要 C++。
#
# 保留这个文件只为一件事：**还有 C++ 代码要调内核**（第三方 SDK 只给 C++ 头、
# 或者旧插件还在用老的 JNI 方法名）。那种情况下把本文件挂进 externalNativeBuild，
# 它会：
#
#   1. 用 cargo 把 Rust 编成 **staticlib**（libbaicodex.a）；
#   2. 把 rust_bridge.cpp 与它一起链成 libbai_bridge.so。
#
# ============================================================================
# 一个必须注意的坑
# ============================================================================
#
# 不要让 libbaicodex.so（cdylib）和 libbai_bridge.so（含同一份 Rust 代码的
# 静态副本）在**同一个进程**里同时被加载：那会有两个内核实例，两个 SQLite 连接
# 抢同一个文件，写锁冲突表现为"偶发 database is locked"，极难排查。
#
# 所以二选一：
#   - 纯 Kotlin 前端 → 只加载 libbaicodex.so，删掉 externalNativeBuild；
#   - 需要 C++ 调用 → 只加载 libbai_bridge.so，Kotlin 侧改成通过它调（不推荐，
#     徒增一层，但如果老代码动不了，这是唯一安全的选择）。
#
# ============================================================================

cmake_minimum_required(VERSION 3.22)
project(baicodex_bridge CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# —— 1. 用 cargo 编 Rust 静态库 ——
#
# 这里假设 Rust 工程与本目录的相对位置是 ../../../../..（见 RUST_DIR）。
# 如果放别处，改这一行即可。`--target` 由 Gradle 传进来的 ANDROID_ABI 决定，
# 所以这里做一次映射。CARGO_TARGET_DIR 固定到 build 目录，避免污染源码树。

set(RUST_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../..")
set(RUST_OUT "${CMAKE_CURRENT_BINARY_DIR}/rust")

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(RUST_TARGET "aarch64-linux-android")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(RUST_TARGET "armv7-linux-androideabi")
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(RUST_TARGET "x86_64-linux-android")
elseif(ANDROID_ABI STREQUAL "x86")
    set(RUST_TARGET "i686-linux-android")
else()
    message(FATAL_ERROR "不认识的 ABI：${ANDROID_ABI}")
endif()

set(RUST_LIB "${RUST_OUT}/${RUST_TARGET}/release/libbaicodex.a")

add_custom_command(
    OUTPUT "${RUST_LIB}"
    COMMAND ${CMAKE_COMMAND} -E env
            CARGO_TARGET_DIR=${RUST_OUT}
            cargo build --release --target ${RUST_TARGET}
            --no-default-features --features tui,js
    WORKING_DIRECTORY "${RUST_DIR}"
    COMMENT "编译 Rust 内核（${RUST_TARGET}）"
    VERBATIM
)

add_custom_target(rust_kernel DEPENDS "${RUST_LIB}")

# —— 2. 链接成 .so ——
#
# 静态库里的符号要显式声明为"会被用到"，否则链接器会把整个 Rust 运行时丢掉
# （JNI 符号是运行时按名字找的，链接期看不到引用）。`-Wl,--whole-archive`
# 就是干这个的。

add_library(bai_bridge SHARED rust_bridge.cpp)
add_dependencies(bai_bridge rust_kernel)

target_link_libraries(bai_bridge
    -Wl,--whole-archive "${RUST_LIB}" -Wl,--no-whole-archive
    android
    log
    dl
    m
)

target_include_directories(bai_bridge PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}")
