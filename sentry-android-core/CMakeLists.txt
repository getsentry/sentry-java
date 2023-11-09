cmake_minimum_required(VERSION 3.10)
project(Sentry-Android-Replay LANGUAGES C CXX)

# Add sentry-android shared library
add_library(sentry-android-replay SHARED src/main/jni/sentry-replay.cpp)

## Your-external "mylib", add GLOBAL if the imported library is located in directories above the current.
#add_library( hwui SHARED IMPORTED )
## You can define two import-locations: one for debug and one for release.
#set_target_properties( hwui PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/libhwui.so )
#
#target_link_libraries(sentry-android-replay hwui)
