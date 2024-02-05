cmake_minimum_required(VERSION 3.10)
project(Sentry-Sample LANGUAGES C CXX)

add_library(native-sample SHARED src/main/cpp/native-sample.cpp)

find_package(sentry-android REQUIRED CONFIG)

# make sure that we build it as a shared lib instead of a static lib
set(BUILD_SHARED_LIBS ON)
set(SENTRY_BUILD_SHARED_LIBS ON)

find_library(LOG_LIB log)

target_link_libraries(native-sample PRIVATE
    ${LOG_LIB}
#    sentry-android::sentry-android
)
