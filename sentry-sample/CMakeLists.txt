cmake_minimum_required(VERSION 3.4.1)
project("sentry-sample")

add_library(native-sample SHARED src/main/cpp/native-sample.cpp)
find_library(LOG_LIB log)
target_link_libraries(native-sample ${LOG_LIB})
