cmake_minimum_required(VERSION 3.22.1)
project(baicodex CXX)

# C++ 核心分层：engine(DSL) / jni(桥接) / runtime(治理) / ctx(上下文)
add_library(baicodex SHARED
        engine/dsl.cpp
        jni/dsl_jni.cpp
        runtime/govern.cpp
        ctx/ctx.cpp)

target_include_directories(baicodex PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/engine
        ${CMAKE_CURRENT_SOURCE_DIR}/runtime
        ${CMAKE_CURRENT_SOURCE_DIR}/ctx)

target_compile_features(baicodex PRIVATE cxx_std_17)
target_compile_options(baicodex PRIVATE -Wall -Wextra -fvisibility=hidden)

set_target_properties(baicodex PROPERTIES
        ANDROID_STL c++_static
        CXX_STANDARD 17)
target_link_options(baicodex PRIVATE -Wl,--strip-all -Wl,--gc-sections -s)