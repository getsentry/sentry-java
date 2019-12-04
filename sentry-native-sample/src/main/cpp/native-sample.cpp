#include <jni.h>
#include <android/log.h>
#include <sentry.h>

#define TAG "sentry-nativesample"

extern "C" {

JNIEXPORT void JNICALL Java_io_sentry_nativesample_NativeSample_message(JNIEnv *env, jclass cls) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "About to crash.");
    sentry_value_t event = sentry_value_new_message_event(
      /*   level */ SENTRY_LEVEL_INFO,
      /*  logger */ "custom",
      /* message */ "It works!"
    );
    sentry_capture_event(event);
}

}
