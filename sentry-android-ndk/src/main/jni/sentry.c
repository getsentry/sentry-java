#include <string.h>
#include <sentry.h>
#include <jni.h>
#include <malloc.h>
#include <android/log.h>

struct transport_options {
    jmethodID notify_envelope_mid;
    jclass cls;
    JNIEnv *env;
    char event_path[4096];
};

struct transport_options g_transport_options;

static void send_envelope(const sentry_envelope_t *envelope, void *data) {
    char event_path[4096];
    strcpy(event_path, g_transport_options.event_path);
    strcat(event_path, "/test.envelope");
    sentry_envelope_write_to_file(envelope, event_path);
    jstring jevent_path = (*g_transport_options.env)->NewStringUTF(g_transport_options.env, event_path);
    (*g_transport_options.env)->CallStaticVoidMethod(g_transport_options.env, g_transport_options.cls, g_transport_options.notify_envelope_mid, jevent_path);
}

JNIEXPORT void JNICALL Java_io_sentry_android_ndk_SentryNdk_initSentryNative(JNIEnv *env, jclass cls, jstring cache_dir_path) {
    const char *path = (*env)->GetStringUTFChars(env, cache_dir_path, 0);
    strcpy(g_transport_options.event_path, path);
    g_transport_options.env = env;
    g_transport_options.cls = cls;
    g_transport_options.notify_envelope_mid = (*env)->GetStaticMethodID(env, cls, "notifyNewSerializedEnvelope", "(Ljava/lang/String;)V");

    sentry_options_t *options = sentry_options_new();

    sentry_options_set_transport(options, send_envelope, NULL);

    sentry_options_set_environment(options, "Production");
    sentry_options_set_release(options, "5fd7a6cd");
    sentry_options_set_debug(options, 1);
    sentry_options_set_dsn(options, "http://dfbecfd398754c73b6e8104538e89124@sentry.io/1322857");

    sentry_init(options);

    sentry_value_t event = sentry_value_new_event();
    sentry_value_set_by_key(event, "message",
                            sentry_value_new_string("Hello World!"));

    sentry_capture_event(event);

    sentry_shutdown();

}

