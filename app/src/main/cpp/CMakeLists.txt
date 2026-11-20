# Project:    Baicodex
# Author:     by 沐白/Baix
# License:    AGPL-3.0 (GNU Affero General Public License v3.0)
# Date:       2026-11-20 21:46:58
# Repository: https://github.com/mubai0122/Baicodex
# Website:    https://agent.ai.baiccb.cn/
# 
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published
# by the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.

cmake_minimum_required(VERSION 3.22.1)
project(baicodex C CXX)
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
add_library(bzip2 STATIC
        _third_party/bzip2/blocksort.c
        _third_party/bzip2/bzlib.c
        _third_party/bzip2/compress.c
        _third_party/bzip2/decompress.c
        _third_party/bzip2/crctable.c
        _third_party/bzip2/huffman.c
        _third_party/bzip2/randtable.c)
target_include_directories(bzip2 PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/_third_party/bzip2)
target_compile_options(bzip2 PRIVATE -w)
set_target_properties(bzip2 PROPERTIES C_STANDARD 99 C_STANDARD_REQUIRED ON POSITION_INDEPENDENT_CODE ON)

add_library(baicodex SHARED
        core/base/bytes.cpp
        core/base/kv.cpp
        core/base/marks.cpp
        core/base/text.cpp
        core/security/mask/mask.cpp
        core/security/mask/patterns.cpp
        core/security/mask/rules.cpp
        core/security/mask/scan.cpp
        core/security/crypto/crypto.cpp
        core/feature/miot/packet.cpp
        core/feature/miot/udp.cpp
        ffi/archive_jni.cpp
        ffi/crypto_ffi.cpp
        ffi/miot_ffi.cpp
        ffi/mask_ffi.cpp
        ffi/mask_jni.cpp
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
        jni/calc_jni.cpp
        core/feature/archive/tarbz2.cpp
        core/feature/llm/llm.cpp
        jni/llm_jni.cpp
        jni/crypto_jni.cpp
        jni/dsl_jni.cpp
        jni/edit_jni.cpp
        jni/guard_jni.cpp
        jni/js_jni.cpp
        jni/kernel_jni.cpp
        jni/miot_jni.cpp
        jni/net_jni.cpp
        jni/secret_jni.cpp)
target_include_directories(baicodex PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
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
        set(RUST_MANIFEST ${CMAKE_CURRENT_SOURCE_DIR}/../rust/Cargo.toml)
        if(DEFINED ENV{HOME} AND NOT "$ENV{HOME}" STREQUAL "")
            set(RUST_TARGET_DIR "$ENV{HOME}/.bcx-rust-target/baicodex")
        else()
            set(RUST_TARGET_DIR ${CMAKE_BINARY_DIR}/rust)
        endif()
        set(RUST_LIB ${RUST_TARGET_DIR}/aarch64-linux-android/release/libbaicodex.a)
        add_custom_target(baicodex_rust ALL
                COMMAND ${CMAKE_COMMAND} -E env CARGO_TARGET_DIR=${RUST_TARGET_DIR}
                        ${CARGO_BIN} build --release --target aarch64-linux-android
                        --manifest-path ${RUST_MANIFEST}
                BYPRODUCTS ${RUST_LIB}
                COMMENT "cargo build baicodex (aarch64-linux-android)"
                VERBATIM)
        add_dependencies(baicodex baicodex_rust)
        target_compile_definitions(baicodex PRIVATE BAI_RUST=1)
        target_link_libraries(baicodex PRIVATE ${RUST_LIB} log dl m)
        message(STATUS "Rust 层启用：${RUST_LIB}")
    else()
        message(STATUS "没找到 cargo，Rust 层跳过（该 ABI 走 C++ 实现）")
    endif()
endif()
if(DEFINED BAI_KEY_HEX AND NOT BAI_KEY_HEX STREQUAL "")
    string(REGEX REPLACE "(..)" "0x\\1," BAI_KEY_BYTES "${BAI_KEY_HEX}")
    target_compile_definitions(baicodex PRIVATE "BAI_KEY_BYTES=${BAI_KEY_BYTES}")
endif()
set_source_files_properties(image/blur.cpp PROPERTIES COMPILE_OPTIONS "-O3")
target_compile_options(baicodex PRIVATE -Wall -Wextra -fvisibility=hidden)
set_target_properties(baicodex PROPERTIES
        ANDROID_STL c++_static
        CXX_STANDARD 17)
target_link_options(baicodex PRIVATE -Wl,--strip-all -Wl,--gc-sections -s)
set(BAI_OCL_DIR ${CMAKE_CURRENT_SOURCE_DIR}/_third_party/opencl)
set(BAI_OCL_LIB ${BAI_OCL_DIR}/${ANDROID_ABI}/libOpenCL.so)
if(ANDROID_ABI STREQUAL "arm64-v8a"
   AND EXISTS ${BAI_OCL_LIB}
   AND EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/_third_party/opencl-headers/CL/cl.h)
    set(GGML_OPENCL ON CACHE BOOL "" FORCE)
    set(GGML_OPENCL_EMBED_KERNELS ON CACHE BOOL "" FORCE)
    set(GGML_OPENCL_USE_ADRENO_KERNELS ON CACHE BOOL "" FORCE)
    set(GGML_OPENCL_TARGET_VERSION 300 CACHE STRING "" FORCE)
    set(OpenCL_INCLUDE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/_third_party/opencl-headers CACHE PATH "" FORCE)
    set(OpenCL_LIBRARY ${BAI_OCL_LIB} CACHE FILEPATH "" FORCE)
    add_compile_definitions(GGML_USE_OPENCL)
    message(STATUS "bai: OpenCL 后端开启（${ANDROID_ABI}）")
else()
    set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
    message(STATUS "bai: 缺 ${BAI_OCL_LIB}，GPU offload 关闭，只编 CPU")
endif()

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_STATIC ON CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)
set(GGML_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_IS_DEV OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_MTMD OFF CACHE BOOL "" FORCE)
set(LLAMA_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
#v7a 没有 v8.2 FP16 NEON 指令（vld1q_f16），llamafile sgemm 在 v7a 上编不过，按 ABI 分别开
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_LLAMAFILE ON CACHE BOOL "" FORCE)
else()
    set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
endif()
add_subdirectory(_third_party/llama.cpp EXCLUDE_FROM_ALL)
target_link_libraries(baicodex PRIVATE
        ${OSSL}/${CMAKE_ANDROID_ARCH_ABI}/libssl.a
        ${OSSL}/${CMAKE_ANDROID_ARCH_ABI}/libcrypto.a
        llama ggml
        quickjs bzip2 m log z)