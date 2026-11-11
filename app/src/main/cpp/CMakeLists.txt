cmake_minimum_required(VERSION 3.22.1)
project(baicodex C CXX)

#cpp/ 目录职责，新增文件按此归位：
#  core/base/      与业务无关的基础工具：字节、KV 文本、掩码写入、扫描游走
#  core/security/  安全相关：数据输出脱敏、加解密、常量时间比较
#  core/feature/   功能相关：与安全无关的业务能力
#  ffi/            纯 C ABI 薄壳：Java_* 入口 + 导出给 Rust/C++ 双实现的同名符号
#  _third_party/   第三方源码：quickjs、android_openssl。只读不改
#
# 迁移中：以下旧目录按上面的分类逐步搬走，搬完即删，不再往里加新文件
#  tools/ -> core/feature/  image/ -> core/feature/  js/ -> core/feature/
#  net/   -> core/feature/  runtime/ -> core/security/  jni/ -> ffi/

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
        core/feature/llm/llm.cpp
        jni/llm_jni.cpp
        jni/crypto_jni.cpp
        jni/dsl_jni.cpp
        jni/edit_jni.cpp
        jni/guard_jni.cpp
        jni/js_jni.cpp
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
        set(RUST_MANIFEST ${CMAKE_CURRENT_SOURCE_DIR}/../rust/baiguard/Cargo.toml)
        set(RUST_TARGET_DIR ${CMAKE_BINARY_DIR}/rust)
        set(RUST_LIB ${RUST_TARGET_DIR}/aarch64-linux-android/release/libbaiguard.a)
        #Rust 侧已拆成多模块，DEPENDS 必须把 src 下所有 .rs 都算上，改动子模块才会重编
        file(GLOB_RECURSE RUST_SOURCES CONFIGURE_DEPENDS
                ${CMAKE_CURRENT_SOURCE_DIR}/../rust/baiguard/src/*.rs)
        add_custom_command(
                OUTPUT ${RUST_LIB}
                COMMAND ${CMAKE_COMMAND} -E env CARGO_TARGET_DIR=${RUST_TARGET_DIR}
                        ${CARGO_BIN} build --release --target aarch64-linux-android
                        --manifest-path ${RUST_MANIFEST}
                DEPENDS ${RUST_MANIFEST} ${RUST_SOURCES}
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
#GPU offload：只用 OpenCL 后端（Adreno 750/810/830 是官方验证过的路线，Vulkan 在 Android 上基本不可用）
#头文件用 Khronos OpenCL-Headers；库用从设备 /vendor 拉出来的 libOpenCL.so 当链接期桩，
#运行期由系统 linker 去 /vendor/lib(64) 解析真实实现，这个桩不会被打进 APK
set(BAI_OCL_DIR ${CMAKE_CURRENT_SOURCE_DIR}/_third_party/opencl)
set(BAI_OCL_LIB ${BAI_OCL_DIR}/${ANDROID_ABI}/libOpenCL.so)
#注意 v7a 编不过 ggml-opencl（32 位 size_t 从 int64_t 窄化报错，属上游源码问题），只在 arm64 开
#头文件和 stub 缺任一都退回 CPU：CI 上没有设备 vendor 库，不能因此编不过
if(ANDROID_ABI STREQUAL "arm64-v8a"
   AND EXISTS ${BAI_OCL_LIB}
   AND EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/_third_party/opencl-headers/CL/cl.h)
    set(GGML_OPENCL ON CACHE BOOL "" FORCE)
    set(GGML_OPENCL_EMBED_KERNELS ON CACHE BOOL "" FORCE)
    set(GGML_OPENCL_USE_ADRENO_KERNELS ON CACHE BOOL "" FORCE)
    set(GGML_OPENCL_TARGET_VERSION 300 CACHE STRING "" FORCE)
    set(OpenCL_INCLUDE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/_third_party/opencl-headers CACHE PATH "" FORCE)
    set(OpenCL_LIBRARY ${BAI_OCL_LIB} CACHE FILEPATH "" FORCE)
    #ggml 是 INTERFACE target，它身上的 GGML_USE_OPENCL 传不到编译 ggml-backend-reg.cpp 的 ggml-base，
    #结果 register_backend(ggml_backend_opencl_reg()) 被 #ifdef 剔掉、整个后端不参与链接。必须在 add_subdirectory 前全局定义
    add_compile_definitions(GGML_USE_OPENCL)
    message(STATUS "bai: OpenCL 后端开启（${ANDROID_ABI}）")
else()
    set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
    message(STATUS "bai: 缺 ${BAI_OCL_LIB}，GPU offload 关闭，只编 CPU")
endif()

#本地模型：llama.cpp 静态链进来，只留 llama + ggml CPU 后端，其余组件全关
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
        quickjs m log z)