cmake_minimum_required(VERSION 3.22.1)

project(sobok C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# One self-contained libsobok.so: whisper + ggml are linked statically.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_IS_DEV OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)

# CPU-only mobile build: no OpenMP in the NDK, no native tuning, no dlopen
# backends. The vendored tree contains only the CPU backend.
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)
set(GGML_ALL_WARNINGS OFF CACHE BOOL "" FORCE)

add_subdirectory(whisper.cpp)

# ---------------------------------------------------------------------------
# Force optimization on the inference hot paths.
#
# The Android Gradle Plugin ties CMAKE_BUILD_TYPE to the Gradle build type, so a
# plain `debug` build compiles whisper.cpp/ggml with -O0. On device that turns a
# 12 s clip into minutes of CPU inference. target_compile_options are appended
# *after* the per-config flags on the compiler command line, so -O3 here wins
# over the debug -O0 while leaving Release builds (already optimized) unchanged.
#
# Deliberately NOT adding -march=armv8.2-a+dotprod+fp16: those are optional arm64
# features and forcing them can SIGILL on older phones. ggml only emits those
# kernels when the compiler defines __ARM_FEATURE_DOTPROD /
# __ARM_FEATURE_FP16_VECTOR_ARITHMETIC, which requires an ARCH flag or the
# GGML_CPU_ALL_VARIANTS path -- revisit later with real-device testing.
# ---------------------------------------------------------------------------
foreach (sobok_optimized whisper ggml ggml-base ggml-cpu)
    if (TARGET ${sobok_optimized})
        target_compile_options(${sobok_optimized} PRIVATE -O3)
    endif()
endforeach()

add_library(sobok SHARED whisper_jni.cpp)

target_include_directories(sobok PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp/ggml/include
)

target_link_libraries(sobok PRIVATE whisper android log)

# Required for Android 15+ 16 KB page-size devices.
target_link_options(sobok PRIVATE "-Wl,-z,max-page-size=16384")
