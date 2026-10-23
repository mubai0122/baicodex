cmake_minimum_required(VERSION 3.22.1)
project(baidsl CXX)

# skill 模板 DSL 引擎：校验与生成共用 dsl.cpp 一份实现，JNI 只做字符串搬运
add_library(baidsl SHARED
        dsl.cpp
        dsl_jni.cpp)

target_compile_features(baidsl PRIVATE cxx_std_17)
target_compile_options(baidsl PRIVATE -Wall -Wextra -fvisibility=hidden)

# 静态链 libc++：只有一个 .so，少了额外的 c++_shared 依赖
set_target_properties(baidsl PROPERTIES
        ANDROID_STL c++_static
        CXX_STANDARD 17)