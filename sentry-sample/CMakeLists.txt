cmake_minimum_required(VERSION 3.10)
project(Sentry-Sample LANGUAGES C CXX)

add_library(native-sample SHARED src/main/cpp/native-sample.cpp)

add_subdirectory(../sentry-android-ndk/${SENTRY_NATIVE_SRC} sentry_build)

find_library(LOG_LIB log)

target_link_libraries(native-sample PRIVATE
    ${LOG_LIB}
    $<BUILD_INTERFACE:sentry::sentry>
)
