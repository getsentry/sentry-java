cmake_minimum_required(VERSION 3.7)
project(sentry_java_agent)

set(CMAKE_CXX_STANDARD 14)

set(SOURCE_FILES agent.cpp lib.cpp lib.h)
add_library(sentry_agent SHARED ${SOURCE_FILES})

include_directories($ENV{JAVA_HOME}/include)

if (${CMAKE_SYSTEM_NAME} MATCHES "Darwin")
    include_directories($ENV{JAVA_HOME}/include/darwin)
endif()
