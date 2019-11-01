#include <jni.h>
#include <signal.h>
#include <android/log.h>

#define TAG "sentry-android-sample"

extern "C" {

JNIEXPORT void JNICALL Java_io_sentry_sample_NativeSample_crash(JNIEnv *env) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "About to raise SIGSEGV.");
    raise(SIGSEGV);
}

}
