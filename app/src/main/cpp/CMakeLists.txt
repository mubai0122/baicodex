cmake_minimum_required(VERSION 3.22.1)
project(baicodex CXX)

# C++ 核心分层：engine(DSL) / net(HTTPS) / jni(桥接) / runtime(治理) / ctx(上下文)
add_library(baicodex SHARED
        engine/dsl.cpp
        net/http.cpp
        jni/dsl_jni.cpp
        jni/net_jni.cpp
        runtime/govern.cpp
        ctx/ctx.cpp)

target_include_directories(baicodex PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/engine
        ${CMAKE_CURRENT_SOURCE_DIR}/net
        ${CMAKE_CURRENT_SOURCE_DIR}/runtime
        ${CMAKE_CURRENT_SOURCE_DIR}/ctx)

# OpenSSL 3 静态库：C 层自己走 TLS，图标这类小请求不再依赖 JVM 的 HttpURLConnection
set(OSSL ${CMAKE_CURRENT_SOURCE_DIR}/_third_party/android_openssl-master/ssl_3)
target_include_directories(baicodex PRIVATE ${OSSL}/include)

target_compile_features(baicodex PRIVATE cxx_std_17)
target_compile_options(baicodex PRIVATE -Wall -Wextra -fvisibility=hidden)
set_target_properties(baicodex PROPERTIES
        ANDROID_STL c++_static
        CXX_STANDARD 17)
target_link_options(baicodex PRIVATE -Wl,--strip-all -Wl,--gc-sections -s)
target_link_libraries(baicodex PRIVATE
        ${OSSL}/${CMAKE_ANDROID_ARCH_ABI}/libssl.a
        ${OSSL}/${CMAKE_ANDROID_ARCH_ABI}/libcrypto.a
        log z)
