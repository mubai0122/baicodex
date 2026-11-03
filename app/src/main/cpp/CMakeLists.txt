cmake_minimum_required(VERSION 3.22.1)
project(baicodex C CXX)

#cpp/ 目录职责，新增文件按此归位：
#  _third_party/  第三方源码：quickjs、android_openssl。只读不改
#  tools/         工具实现：工具模板 DSL（shell 命令 / http 报文渲染与校验）、edit_file 按行定位
#  image/         纯算法：像素处理（盒式模糊），不认识 JNI
#  js/            QuickJS 运行时封装 + 会话层（句柄、宿主工具桥回调）
#  net/           HTTPS 客户端
#  runtime/       加解密与预算治理：secret 落盘加密、guard 数据保障、budget 超限截断
#  jni/           只有 JNI 薄壳：Java_* 入口 + jni_util.h 字符串胶水，禁止写业务逻辑

add_library(quickjs STATIC
        _third_party/quickjs/quickjs.c
        _third_party/quickjs/dtoa.c
        _third_party/quickjs/libregexp.c
        _third_party/quickjs/libunicode.c
        _third_party/quickjs/cutils.c)
target_include_directories(quickjs PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/_third_party/quickjs)
target_compile_definitions(quickjs PRIVATE _GNU_SOURCE CONFIG_VERSION=\"2026-06-04\")
target_compile_options(quickjs PRIVATE -O2 -fwrapv -Wno-unused -Wno-sign-compare
        -Wno-missing-braces -Wno-format-truncation -Wno-unused-but-set-variable)
set_target_properties(quickjs PROPERTIES C_STANDARD 11 C_STANDARD_REQUIRED ON)

add_library(baicodex SHARED
        tools/dsl.cpp
        tools/edit.cpp
        image/blur.cpp
        js/bridge.cpp
        js/qjs.cpp
        net/http.cpp
        runtime/budget.cpp
        runtime/guard.cpp
        runtime/secret.cpp
        jni/blur_jni.cpp
        jni/dsl_jni.cpp
        jni/edit_jni.cpp
        jni/guard_jni.cpp
        jni/js_jni.cpp
        jni/net_jni.cpp
        jni/secret_jni.cpp)
target_include_directories(baicodex PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/tools
        ${CMAKE_CURRENT_SOURCE_DIR}/image
        ${CMAKE_CURRENT_SOURCE_DIR}/jni
        ${CMAKE_CURRENT_SOURCE_DIR}/js
        ${CMAKE_CURRENT_SOURCE_DIR}/net
        ${CMAKE_CURRENT_SOURCE_DIR}/runtime)

set(OSSL ${CMAKE_CURRENT_SOURCE_DIR}/_third_party/android_openssl-master/ssl_3)
target_include_directories(baicodex PRIVATE ${OSSL}/include)
target_compile_features(baicodex PRIVATE cxx_std_17)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    find_program(CARGO_BIN cargo)
    if(CARGO_BIN)
        set(RUST_MANIFEST ${CMAKE_CURRENT_SOURCE_DIR}/../rust/baiguard/Cargo.toml)
        set(RUST_TARGET_DIR ${CMAKE_BINARY_DIR}/rust)
        set(RUST_LIB ${RUST_TARGET_DIR}/aarch64-linux-android/release/libbaiguard.a)
        add_custom_command(
                OUTPUT ${RUST_LIB}
                COMMAND ${CMAKE_COMMAND} -E env CARGO_TARGET_DIR=${RUST_TARGET_DIR}
                        ${CARGO_BIN} build --release --target aarch64-linux-android
                        --manifest-path ${RUST_MANIFEST}
                DEPENDS ${RUST_MANIFEST}
                        ${CMAKE_CURRENT_SOURCE_DIR}/../rust/baiguard/src/lib.rs
                COMMENT "cargo build baiguard (aarch64-linux-android)"
                VERBATIM)
        add_custom_target(baiguard_rust DEPENDS ${RUST_LIB})
        add_dependencies(baicodex baiguard_rust)
        target_compile_definitions(baicodex PRIVATE BAI_RUST=1)
        target_link_libraries(baicodex PRIVATE ${RUST_LIB} log dl m)
        message(STATUS "Rust 层启用：${RUST_LIB}")
    else()
        message(STATUS "没找到 cargo，Rust 层跳过（该 ABI 走 C++ 实现）")
    endif()
endif()
if(DEFINED BAI_KEY_HEX AND NOT BAI_KEY_HEX STREQUAL "")
    #拆成字节数组再下发：整串十六进制留在 .so 里会被 strings 一眼看到
    string(REGEX REPLACE "(..)" "0x\\1," BAI_KEY_BYTES "${BAI_KEY_HEX}")
    target_compile_definitions(baicodex PRIVATE "BAI_KEY_BYTES=${BAI_KEY_BYTES}")
endif()
#模糊是纯计算，debug 默认 -O0 会比 Kotlin 还慢，单独拉到 -O3
set_source_files_properties(image/blur.cpp PROPERTIES COMPILE_OPTIONS "-O3")
target_compile_options(baicodex PRIVATE -Wall -Wextra -fvisibility=hidden)
set_target_properties(baicodex PROPERTIES
        ANDROID_STL c++_static
        CXX_STANDARD 17)
target_link_options(baicodex PRIVATE -Wl,--strip-all -Wl,--gc-sections -s)
target_link_libraries(baicodex PRIVATE
        ${OSSL}/${CMAKE_ANDROID_ARCH_ABI}/libssl.a
        ${OSSL}/${CMAKE_ANDROID_ARCH_ABI}/libcrypto.a
        quickjs m log z)