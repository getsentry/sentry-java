#include <string>
#include "jvmti.h"

#ifndef SENTRY_JAVA_AGENT_LIB_H
#define SENTRY_JAVA_AGENT_LIB_H

enum Level {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR
};

void log(Level level, std::string message);

jobjectArray buildStackTraceFrames(jvmtiEnv* jvmti, JNIEnv *env, jthread thread,
                                   jint start_depth);

#endif //SENTRY_JAVA_AGENT_LIB_H
