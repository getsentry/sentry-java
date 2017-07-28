cmake_minimum_required(VERSION 3.2)
project(sentry_java_agent)

set(CMAKE_CXX_STANDARD 14)

set(SOURCE_FILES agent.cpp lib.cpp lib.h)
add_library(sentry_agent SHARED ${SOURCE_FILES})
set_target_properties(mylib PROPERTIES COMPILE_FLAGS "-m32" LINK_FLAGS "-m32")

include_directories($ENV{JAVA_HOME}/include)

if (${CMAKE_SYSTEM_NAME} MATCHES "Darwin")
    include_directories($ENV{JAVA_HOME}/include/darwin)
elseif (${CMAKE_SYSTEM_NAME} MATCHES "Linux")
    include_directories($ENV{JAVA_HOME}/include/linux)
endif()
