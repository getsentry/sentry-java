cmake_minimum_required(VERSION 3.4.1)
project("sentry-sample")

add_subdirectory(../sentry-android-ndk/${SENTRY_NATIVE_SRC} "${CMAKE_CURRENT_BINARY_DIR}/sentry-native")
include_directories(../sentry-android-ndk/${SENTRY_NATIVE_SRC}/include)

find_library(LOG_LIB log)
add_library(native-sample SHARED src/main/cpp/native-sample.cpp)

target_link_libraries(native-sample "sentry" ${LOG_LIB})