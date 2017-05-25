#include "jvmti.h"
#include <iostream>
#include "lib.h"

enum Level {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR
};

static const std::string LEVEL_STRINGS[] = {
        "TRACE",
        "DEBUG",
        "INFO",
        "WARN",
        "ERROR"
};

static Level LOG_LEVEL = WARN;

void log(Level level, std::string message) {
    if (level >= LOG_LEVEL) {
        std::cerr << LEVEL_STRINGS[level] << " [Sentry Agent]: " << message << std::endl;
    }
}

void JNICALL VMStart(jvmtiEnv *jvmti, JNIEnv *env) {
    log(TRACE, "VMStart called.");
    // TODO: do we need to do anything here?
    log(TRACE, "VMStart exit.");
}

void JNICALL ExceptionCallback(jvmtiEnv *jvmti, JNIEnv *env, jthread thread,
                                      jmethodID method, jlocation location, jobject exception,
                                      jmethodID catch_method, jlocation catch_location) {
    log(TRACE, "ExceptionCallback called.");

    char *class_name = (char *) "io/sentry/jvmti/LocalsCache";
    char *method_name = (char *) "setResult";
    char *signature = (char *) "([Lio/sentry/jvmti/Frame;)V";

    jclass callback_class = nullptr;
    jmethodID callback_method_id = nullptr;

    callback_class = env->FindClass(class_name);
    if (callback_class == nullptr) {
        env->ExceptionClear();
        log(TRACE, "Unable to locate callback class.");
        return;
    }

    callback_method_id = env->GetStaticMethodID(callback_class, method_name, signature);
    if (callback_method_id == nullptr) {
        log(TRACE, "Unable to locate static setResult method.");
        return;
    }

    jobjectArray frames;
    jint start_depth = 0;
    jboolean get_locals = JNI_TRUE;
    frames = buildStackTraceFrames(jvmti, env, thread, start_depth, get_locals);

    env->CallStaticVoidMethod(callback_class, callback_method_id, frames);

    log(TRACE, "ExceptionCallback exit.");
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
    // TODO: JNI abort doesn't do I expect ... exit() or just mark a flag that the agent is disabled?
    // TODO: use options instead of env
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
    callbacks.VMStart = &VMStart;
    jvmti_error = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
    if (jvmti_error != JVMTI_ERROR_NONE) {
        log(ERROR, "Unable to the necessary JVMTI callbacks.");
        return JNI_ABORT;
    }

    jvmti_error = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_START, nullptr);
    if (jvmti_error != JVMTI_ERROR_NONE) {
        log(ERROR, "Unable to register the VMStart callback.");
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

JNIEXPORT void JNICALL Agent_OnUnload(JavaVM *vm) {
    log(TRACE, "Unload called.");

    log(TRACE, "Unload exit.");
}
