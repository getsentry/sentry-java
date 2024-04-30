cmake_minimum_required(VERSION 3.10)
project(Sentry-Sample LANGUAGES C CXX)

add_library(native-sample SHARED src/main/cpp/native-sample.cpp)

find_package(sentry-ndk REQUIRED CONFIG)

find_library(LOG_LIB log)

target_link_libraries(native-sample PRIVATE
    ${LOG_LIB}
    sentry-ndk::sentry-android
    sentry-ndk::sentry
)
