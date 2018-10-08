// TODO: cache all FindClass/Find* calls globally
// TODO: better error messages with (void) jvmti->GetErrorName(errnum, &errnum_str);
// TODO: do we need any locking? jrawMonitorID lock; jvmti->RawMonitorEnter(lock); jvmti->RawMonitorExit(lock);
// TODO: use *options instead of env for log level

#include "jvmti.h"
#include <iostream>
#include <cstring>
#include "lib.h"

extern Level LOG_LEVEL;

static void JNICALL ExceptionCallback(jvmtiEnv *jvmti, JNIEnv *env, jthread thread,
                                      jmethodID method, jlocation location, jobject exception,
                                      jmethodID catch_method, jlocation catch_location) {
    log(TRACE, "ExceptionCallback called.");

    jclass sentry_class = env->FindClass("io/sentry/Sentry");
    if (sentry_class == nullptr) {
        env->ExceptionClear();
        log(TRACE, "Unable to locate Sentry class.");
        return;
    }

    jfieldID storedClientId = env->GetStaticFieldID(sentry_class, "storedClient", "Lio/sentry/SentryClient;");
    jobject storedClientVal = env->GetStaticObjectField(sentry_class, storedClientId);
    if (storedClientVal == nullptr) {
        env->ExceptionClear();
        log(TRACE, "No stored SentryClient.");
        return;
    }

    jclass framecache_class = env->FindClass("io/sentry/jvmti/FrameCache");
    if (framecache_class == nullptr) {
        env->ExceptionClear();
        log(TRACE, "Unable to locate FrameCache class.");
        return;
    }

    jmethodID should_cache_method = env->GetStaticMethodID(framecache_class,
                                                           "shouldCacheThrowable",
                                                           "(Ljava/lang/Throwable;I)Z");
    if (should_cache_method == nullptr) {
        log(TRACE, "Unable to locate static FrameCache.shouldCacheThrowable method.");
        return;
    }

    jint num_frames;
    jvmtiError jvmti_error = jvmti->GetFrameCount(thread, &num_frames);
    if (jvmti_error != JVMTI_ERROR_NONE) {
        log(ERROR, "Could not get the frame count.");
        return;
    }

    jboolean shouldCache = env->CallStaticBooleanMethod(framecache_class,
                                                        should_cache_method,
                                                        exception,
                                                        num_frames);
    if (!((bool) shouldCache)) {
        return;
    }

    jmethodID framecache_add_method = env->GetStaticMethodID(framecache_class,
                                                   "add",
                                                   "(Ljava/lang/Throwable;[Lio/sentry/jvmti/Frame;)V");
    if (framecache_add_method == nullptr) {
        log(TRACE, "Unable to locate static FrameCache.add method.");
        return;
    }

    jint start_depth = 0;
    jobjectArray frames = buildStackTraceFrames(jvmti, env, thread, start_depth, num_frames);

    env->CallStaticVoidMethod(framecache_class, framecache_add_method, exception, frames);

    log(TRACE, "ExceptionCallback exit.");
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
    char *env_log_level = std::getenv("SENTRY_AGENT_LOG_LEVEL");
    if (env_log_level != nullptr) {
        std::string env_log_level_str(env_log_level);
        for (auto &c: env_log_level_str) c = (char) toupper(c);
        if (env_log_level_str.compare("TRACE") == 0) {
            LOG_LEVEL = TRACE;
        } else if (env_log_level_str.compare("DEBUG") == 0) {
            LOG_LEVEL = DEBUG;
        } else if (env_log_level_str.compare("INFO") == 0) {
            LOG_LEVEL = INFO;
        } else if (env_log_level_str.compare("WARN") == 0) {
            LOG_LEVEL = WARN;
        } else if (env_log_level_str.compare("ERROR") == 0) {
            LOG_LEVEL = ERROR;
        } else {
            log(ERROR, "Unknown log level: " + env_log_level_str);
            return JNI_ABORT;
        }
    }

    log(TRACE, "OnLoad called.");

    jvmtiEnv *jvmti;
    jint jvmti_error;

    jvmti_error = vm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_0);
    if (jvmti_error != JVMTI_ERROR_NONE || jvmti == nullptr) {
        log(ERROR, "Unable to access JVMTI Version 1.");
        return JNI_ABORT;
    }

    jvmtiCapabilities capabilities;
    memset(&capabilities, 0, sizeof(capabilities));
    capabilities.can_access_local_variables = 1;
    capabilities.can_generate_exception_events = 1;
    capabilities.can_get_line_numbers = 1;
    jint capabilities_error = jvmti->AddCapabilities(&capabilities);
    if (capabilities_error != JVMTI_ERROR_NONE) {
        log(ERROR, "Unable to get the necessary JVMTI capabilities.");
        return JNI_ABORT;
    }

    jvmtiEventCallbacks callbacks;
    memset(&callbacks, 0, sizeof(callbacks));
    callbacks.Exception = &ExceptionCallback;
    jvmti_error = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
    if (jvmti_error != JVMTI_ERROR_NONE) {
        log(ERROR, "Unable to the necessary JVMTI callbacks.");
        return JNI_ABORT;
    }

    jvmti_error = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_EXCEPTION, nullptr);
    if (jvmti_error != JVMTI_ERROR_NONE) {
        log(ERROR, "Unable to register the exception callback.");
        return JNI_ABORT;
    }

    log(TRACE, "OnLoad exit.");
    return JNI_OK;
}
