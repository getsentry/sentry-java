#include <jni.h>
#include <android/log.h>
#include <sentry.h>

#define TAG "sentry-sample"

extern "C" {

JNIEXPORT void JNICALL Java_io_sentry_samples_android_NativeSample_crash(JNIEnv *env, jclass cls) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "About to crash.");
    char *ptr = 0;
    *ptr += 1;
}

JNIEXPORT void JNICALL Java_io_sentry_samples_android_NativeSample_message(JNIEnv *env, jclass cls) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Sending message.");
    sentry_value_t event = sentry_value_new_message_event(
            /*   level */ SENTRY_LEVEL_INFO,
            /*  logger */ "custom",
            /* message */ "It works!"
    );
    sentry_capture_event(event);
}

[[gnu::noinline]]
static void idle_pointlessly() {
    static const volatile int x = 42;
    (void)x;
}

[[gnu::noinline]]
static void loop_eternally() {
    while (true) {
        idle_pointlessly();
    }
}

[[gnu::noinline]]
static void keep_object_locked(JNIEnv* env, jobject obj) {
    env->MonitorEnter(obj);
    loop_eternally();
    env->MonitorExit(obj);
}

JNIEXPORT void JNICALL Java_io_sentry_samples_android_NativeSample_freezeMysteriously(JNIEnv *env, jclass cls, jobject obj) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "About to lock object eternally.");
    keep_object_locked(env, obj);
}

}
