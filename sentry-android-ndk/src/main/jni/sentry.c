#include <string.h>
#include <sentry.h>
#include <jni.h>
#include <malloc.h>
#include <android/log.h>

struct transport_options {
    jmethodID notify_envelope_mid;
    jclass cls;
    JNIEnv *env;
    char outbox_path[4096];
    int debug;
};

struct transport_options g_transport_options;

static void send_envelope(const sentry_envelope_t *envelope, void *data) {
    char envelope_id_str[40];
    char outbox_path[4096];

    sentry_uuid_t envelope_id = sentry_uuid_new_v4();
    sentry_uuid_as_string(&envelope_id, envelope_id_str);

    strcpy(outbox_path, g_transport_options.outbox_path);
    strcat(outbox_path, "/");
    strcat(outbox_path, envelope_id_str);
    sentry_envelope_write_to_file(envelope, outbox_path);

    jstring jevent_path = (*g_transport_options.env)->NewStringUTF(g_transport_options.env, outbox_path);
    (*g_transport_options.env)->CallStaticVoidMethod(
            g_transport_options.env, g_transport_options.cls,
            g_transport_options.notify_envelope_mid, jevent_path);
}

JNIEXPORT void JNICALL Java_io_sentry_android_ndk_SentryNdk_initSentryNative(JNIEnv *env, jclass cls, jobject sentry_sdk_options) {
    jclass options_cls = (*env)->GetObjectClass(env, sentry_sdk_options);

    jmethodID outbox_path_mid = (*env)->GetMethodID(env, options_cls, "getOutboxPath", "()Ljava/lang/String;");
    jstring outbox_path = (jstring)(*env)->CallObjectMethod(env, sentry_sdk_options, outbox_path_mid);
    strncpy(g_transport_options.outbox_path, (*env)->GetStringUTFChars(env, outbox_path, 0), sizeof(g_transport_options.outbox_path));

    jmethodID dsn_mid = (*env)->GetMethodID(env, options_cls, "getDsn", "()Ljava/lang/String;");
    jstring dsn = (jstring)(*env)->CallObjectMethod(env, sentry_sdk_options, dsn_mid);

    jmethodID is_debug_mid = (*env)->GetMethodID(env, options_cls, "isDebug", "()Z");
    g_transport_options.debug = (*env)->CallBooleanMethod(env, sentry_sdk_options, is_debug_mid);

    g_transport_options.env = env;
    g_transport_options.cls = cls;
    g_transport_options.notify_envelope_mid = (*env)->GetStaticMethodID(env, cls, "notifyNewSerializedEnvelope", "(Ljava/lang/String;)V");

    sentry_options_t *options = sentry_options_new();
    sentry_options_set_transport(options, send_envelope, NULL);
    sentry_options_set_debug(options, g_transport_options.debug);
    sentry_options_set_dsn(options, (*env)->GetStringUTFChars(env, dsn, 0));
    sentry_init(options);
}

