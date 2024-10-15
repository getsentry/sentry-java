cmake_minimum_required(VERSION 3.10)
project(Sentry-Sample LANGUAGES C CXX)

add_library(native-sample SHARED src/main/cpp/native-sample.cpp)

# make sure that we build it as a shared lib instead of a static lib
set(BUILD_SHARED_LIBS ON)
set(SENTRY_BUILD_SHARED_LIBS ON)

add_subdirectory(../../sentry-android-ndk/${SENTRY_NATIVE_SRC} sentry_build)

find_library(LOG_LIB log)

target_link_libraries(native-sample PRIVATE
    ${LOG_LIB}
    $<BUILD_INTERFACE:sentry::sentry>
)

# Android 15: Support 16KB page sizes
# see https://developer.android.com/guide/practices/page-sizes
target_link_options(native-sample PRIVATE "-Wl,-z,max-page-size=16384")

