#include "jvmti.h"
#include <iostream>

enum Level {
    DEBUG,
    INFO,
    WARN,
    ERROR
};

static const std::string LEVEL_STRINGS[] = {
        "DEBUG",
        "INFO",
        "WARN",
        "ERROR"
};

static Level LOG_LEVEL;

static void log(Level level, std::string message) {
    if (level >= LOG_LEVEL) {
        std::cerr << LEVEL_STRINGS[level] << " [Sentry Agent]: " << message << std::endl;
    }
}

static void JNICALL ExceptionCallback(jvmtiEnv *jvmti, JNIEnv *env, jthread thread,
                                      jmethodID method, jlocation location, jobject exception,
                                      jmethodID catch_method, jlocation catch_location) {
    log(DEBUG, "ExceptionCallback called.");
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
    LOG_LEVEL = WARN;
    char *env_log_level = std::getenv("SENTRY_AGENT_LOG_LEVEL");
    if (env_log_level != nullptr) {
        std::string env_log_level_str(env_log_level);
        for (auto &c: env_log_level_str) c = (char) toupper(c);
        if (env_log_level_str.compare("DEBUG") == 0) {
            LOG_LEVEL = DEBUG;
        } else if (env_log_level_str.compare("INFO") == 0) {
            LOG_LEVEL = INFO;
        } else if (env_log_level_str.compare("WARN") == 0) {
            LOG_LEVEL = WARN;
        } else if (env_log_level_str.compare("ERROR") == 0) {
            LOG_LEVEL = ERROR;
        } else {
            LOG_LEVEL = WARN;
            log(ERROR, "Unknown log level: " + env_log_level_str);
        }
    }

    log(DEBUG, "OnLoad called.");

    jvmtiEnv *jvmti;
    jint jvmti_error = vm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_0);
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
    jint callbacks_error = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
    if (callbacks_error != JVMTI_ERROR_NONE) {
        log(ERROR, "Unable to the necessary JVMTI callbacks.");
        return JNI_ABORT;
    }

    jint notification_error = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_EXCEPTION, nullptr);
    if (notification_error != JVMTI_ERROR_NONE) {
        log(ERROR, "Unable to set the necessary JVMTI event notification mode.");
        return JNI_ABORT;
    }

    return JNI_OK;
}

JNIEXPORT void JNICALL Agent_OnUnload(JavaVM *vm) {
    log(DEBUG, "Unload called.");
}
