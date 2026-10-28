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
add_library(baicodex SHARED
        engine/dsl.cpp
        net/http.cpp
        jni/dsl_jni.cpp
        jni/net_jni.cpp
        jni/js_jni.cpp
        jni/blur_jni.cpp
        js/qjs.cpp
        runtime/govern.cpp
        ctx/ctx.cpp)
target_include_directories(baicodex PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/engine
        ${CMAKE_CURRENT_SOURCE_DIR}/net
        ${CMAKE_CURRENT_SOURCE_DIR}/runtime
        ${CMAKE_CURRENT_SOURCE_DIR}/ctx
        ${CMAKE_CURRENT_SOURCE_DIR}/js)

set(OSSL ${CMAKE_CURRENT_SOURCE_DIR}/_third_party/android_openssl-master/ssl_3)
target_include_directories(baicodex PRIVATE ${OSSL}/include)
target_compile_features(baicodex PRIVATE cxx_std_17)
#模糊是纯整数滑窗，debug 默认 -O0 会慢过 Kotlin，这里单独给这个文件开 -O3
set_source_files_properties(jni/blur_jni.cpp PROPERTIES COMPILE_OPTIONS "-O3")
target_compile_options(baicodex PRIVATE -Wall -Wextra -fvisibility=hidden)
set_target_properties(baicodex PROPERTIES
        ANDROID_STL c++_static
        CXX_STANDARD 17)
target_link_options(baicodex PRIVATE -Wl,--strip-all -Wl,--gc-sections -s)
target_link_libraries(baicodex PRIVATE
        ${OSSL}/${CMAKE_ANDROID_ARCH_ABI}/libssl.a
        ${OSSL}/${CMAKE_ANDROID_ARCH_ABI}/libcrypto.a
        quickjs m log z)