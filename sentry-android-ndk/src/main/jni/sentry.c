#include <string.h>
#include <sentry.h>
#include <jni.h>

struct transport_options {
    jclass cls;
    JNIEnv *env;
    char outbox_path[4096];
    int debug;
};

struct transport_options g_transport_options;

JNIEXPORT void JNICALL Java_io_sentry_android_ndk_NdkScopeObserver_nativeSetTag(JNIEnv *env, jclass cls, jstring key, jstring value) {
    if (key == NULL) {
        return;
    }
    const char *charKey = (*env)->GetStringUTFChars(env, key, 0);
    const char *charValue = NULL;
    if (value != NULL) {
        charValue = (*env)->GetStringUTFChars(env, value, 0);
    }
    sentry_set_tag(charKey, charValue);
}

JNIEXPORT void JNICALL Java_io_sentry_android_ndk_NdkScopeObserver_nativeSetUser(
        JNIEnv *env,
        jclass cls,
        jstring id,
        jstring email,
        jstring ipAddress,
        jstring username) {
    if (id == NULL && email == NULL && ipAddress == NULL && username == NULL) {
        sentry_set_user(sentry_value_new_object());
        return;
    }

    sentry_value_t user = sentry_value_new_object();
    if (id) {
        const char *charId = (*env)->GetStringUTFChars(env, id, 0);
        sentry_value_set_by_key(user, "id", sentry_value_new_string(charId));
    }
    if (email) {
        const char *charEmail = (*env)->GetStringUTFChars(env, email, 0);
        sentry_value_set_by_key(
                user, "email", sentry_value_new_string(charEmail));
    }
    if (ipAddress) {
        const char *charIpAddress = (*env)->GetStringUTFChars(env, ipAddress, 0);
        sentry_value_set_by_key(
                user, "ip_address", sentry_value_new_string(charIpAddress));
    }
    if (username) {
        const char *charUsername = (*env)->GetStringUTFChars(env, username, 0);
        sentry_value_set_by_key(
                user, "username", sentry_value_new_string(charUsername));
    }
    sentry_set_user(user);
}

JNIEXPORT void JNICALL Java_io_sentry_android_ndk_NdkScopeObserver_nativeAddBreadcrumb(
        JNIEnv *env,
        jclass cls,
        jstring level,
        jstring message,
        jstring category,
        jstring type) {
    if (level == NULL && message == NULL && category == NULL && type == NULL) {
        return;
    }
    const char *charMessage = NULL;
    if (message) {
        charMessage = (*env)->GetStringUTFChars(env, message, 0);
    }
    const char *charType = NULL;
    if (type) {
        charType = (*env)->GetStringUTFChars(env, type, 0);
    }
    sentry_value_t crumb
            = sentry_value_new_breadcrumb(charType, charMessage);

    if (category) {
        const char *charCategory = (*env)->GetStringUTFChars(env, category, 0);
        sentry_value_set_by_key(
                crumb, "category", sentry_value_new_string(charCategory));
    }
    if (level) {
        const char *charLevel = (*env)->GetStringUTFChars(env, level, 0);
        sentry_value_set_by_key(
                crumb, "level", sentry_value_new_string(charLevel));
    }

    sentry_add_breadcrumb(crumb);
}



static void send_envelope(sentry_envelope_t *envelope, void *unused_data) {
    (void)unused_data;
    char envelope_id_str[40];
    char outbox_path[4096];

    sentry_uuid_t envelope_id = sentry_uuid_new_v4();
    sentry_uuid_as_string(&envelope_id, envelope_id_str);

    strcpy(outbox_path, g_transport_options.outbox_path);
    strcat(outbox_path, "/");
    strcat(outbox_path, envelope_id_str);
    sentry_envelope_write_to_file(envelope, outbox_path);
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

    jmethodID release_mid = (*env)->GetMethodID(env, options_cls, "getRelease", "()Ljava/lang/String;");
    jstring release = (jstring)(*env)->CallObjectMethod(env, sentry_sdk_options, release_mid);

    jmethodID environment_mid = (*env)->GetMethodID(env, options_cls, "getEnvironment", "()Ljava/lang/String;");
    jstring environment = (jstring)(*env)->CallObjectMethod(env, sentry_sdk_options, environment_mid);

    jmethodID dist_mid = (*env)->GetMethodID(env, options_cls, "getDist", "()Ljava/lang/String;");
    jstring dist = (jstring)(*env)->CallObjectMethod(env, sentry_sdk_options, dist_mid);

    g_transport_options.env = env;
    g_transport_options.cls = cls;

    sentry_options_t *options = sentry_options_new();

    // give sentry-native its own database path it can work with, next to the outbox
    char database_path[4096];
    strncpy(database_path, g_transport_options.outbox_path, 4096);
    char *dir = strrchr(database_path, '/');
    if (dir) {
        strncpy(dir + 1, ".sentry-native", 4096 - (dir + 1 - database_path));
    }
    sentry_options_set_database_path(options, database_path);

    sentry_options_set_transport(
            options, sentry_new_function_transport(send_envelope, NULL));
    sentry_options_set_debug(options, g_transport_options.debug);
    sentry_options_set_dsn(options, (*env)->GetStringUTFChars(env, dsn, 0));

    if (release != NULL) {
        sentry_options_set_release(options, (*env)->GetStringUTFChars(env, release, 0));
    }
    if (environment != NULL) {
        sentry_options_set_environment(options, (*env)->GetStringUTFChars(env, environment, 0));
    }
    if (dist != NULL) {
        sentry_options_set_dist(options, (*env)->GetStringUTFChars(env, dist, 0));
    }
    // session tracking is enabled by default, but the Android SDK already handles it
    sentry_options_set_auto_session_tracking(options, 0);

    sentry_init(options);
}
