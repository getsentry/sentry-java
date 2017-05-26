#ifndef SENTRY_JAVA_AGENT_LIB_H
#define SENTRY_JAVA_AGENT_LIB_H


jobjectArray buildStackTraceFrames(jvmtiEnv* jvmti, JNIEnv *env, jthread thread,
                                   jint start_depth);

#endif //SENTRY_JAVA_AGENT_LIB_H
