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

JNIEXPORT void JNICALL
Java_io_sentry_android_ndk_NativeScope_nativeSetTag(
        JNIEnv *env,
        jclass cls,
        jstring key,
        jstring value) {
    const char *charKey = (*env)->GetStringUTFChars(env, key, 0);
    const char *charValue = (*env)->GetStringUTFChars(env, value, 0);

    sentry_set_tag(charKey, charValue);
}

JNIEXPORT void JNICALL
Java_io_sentry_android_ndk_NativeScope_nativeRemoveTag(JNIEnv *env, jclass cls, jstring key) {
    const char *charKey = (*env)->GetStringUTFChars(env, key, 0);

    sentry_remove_tag(charKey);
}

JNIEXPORT void JNICALL
Java_io_sentry_android_ndk_NativeScope_nativeSetExtra(
        JNIEnv *env,
        jclass cls,
        jstring key,
        jstring value) {
    const char *charKey = (*env)->GetStringUTFChars(env, key, 0);
    const char *charValue = (*env)->GetStringUTFChars(env, value, 0);

    sentry_value_t sentryValue = sentry_value_new_string(charValue);
    sentry_set_extra(charKey, sentryValue);
}

JNIEXPORT void JNICALL
Java_io_sentry_android_ndk_NativeScope_nativeRemoveExtra(JNIEnv *env, jclass cls, jstring key) {
    const char *charKey = (*env)->GetStringUTFChars(env, key, 0);

    sentry_remove_extra(charKey);
}

JNIEXPORT void JNICALL
Java_io_sentry_android_ndk_NativeScope_nativeSetUser(
        JNIEnv *env,
        jclass cls,
        jstring id,
        jstring email,
        jstring ipAddress,
        jstring username) {
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

JNIEXPORT void JNICALL
Java_io_sentry_android_ndk_NativeScope_nativeRemoveUser(JNIEnv *env, jclass cls) {
    sentry_remove_user();
}

JNIEXPORT void JNICALL
Java_io_sentry_android_ndk_NativeScope_nativeAddBreadcrumb(
        JNIEnv *env,
        jclass cls,
        jstring level,
        jstring message,
        jstring category,
        jstring type,
        jstring timestamp,
        jstring data) {
    if (!level && !message && !category && !type) {
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

    if (timestamp) {
        // overwrite timestamp that is already created on sentry_value_new_breadcrumb
        const char *charTimestamp = (*env)->GetStringUTFChars(env, timestamp, 0);
        sentry_value_set_by_key(
                crumb, "timestamp", sentry_value_new_string(charTimestamp));
    }

    if (data) {
        const char *charData = (*env)->GetStringUTFChars(env, data, 0);

        // we create an object because the Java layer parses it as a Map
        sentry_value_t dataObject = sentry_value_new_object();
        sentry_value_set_by_key(dataObject, "data", sentry_value_new_string(charData));

        sentry_value_set_by_key(crumb, "data", dataObject);
    }

    sentry_add_breadcrumb(crumb);
}

static void send_envelope(const sentry_envelope_t *envelope, void *unused_data) {
    (void) unused_data;
    char envelope_id_str[40];
    char outbox_path[4096];

    sentry_uuid_t envelope_id = sentry_uuid_new_v4();
    sentry_uuid_as_string(&envelope_id, envelope_id_str);

    strcpy(outbox_path, g_transport_options.outbox_path);
    strcat(outbox_path, "/");
    strcat(outbox_path, envelope_id_str);
    sentry_envelope_write_to_file(envelope, outbox_path);
}

JNIEXPORT void JNICALL
Java_io_sentry_android_ndk_SentryNdk_initSentryNative(
        JNIEnv *env,
        jclass cls,
        jobject sentry_sdk_options) {
    jclass options_cls = (*env)->GetObjectClass(env, sentry_sdk_options);

    jmethodID outbox_path_mid = (*env)->GetMethodID(env, options_cls, "getOutboxPath",
                                                    "()Ljava/lang/String;");
    jstring outbox_path = (jstring) (*env)->CallObjectMethod(env, sentry_sdk_options,
                                                             outbox_path_mid);
    strncpy(g_transport_options.outbox_path, (*env)->GetStringUTFChars(env, outbox_path, 0),
            sizeof(g_transport_options.outbox_path));

    jmethodID dsn_mid = (*env)->GetMethodID(env, options_cls, "getDsn", "()Ljava/lang/String;");
    jstring dsn = (jstring) (*env)->CallObjectMethod(env, sentry_sdk_options, dsn_mid);

    jmethodID is_debug_mid = (*env)->GetMethodID(env, options_cls, "isDebug", "()Z");
    g_transport_options.debug = (*env)->CallBooleanMethod(env, sentry_sdk_options, is_debug_mid);

    jmethodID release_mid = (*env)->GetMethodID(env, options_cls, "getRelease",
                                                "()Ljava/lang/String;");
    jstring release = (jstring) (*env)->CallObjectMethod(env, sentry_sdk_options, release_mid);

    jmethodID environment_mid = (*env)->GetMethodID(env, options_cls, "getEnvironment",
                                                    "()Ljava/lang/String;");
    jstring environment = (jstring) (*env)->CallObjectMethod(env, sentry_sdk_options,
                                                             environment_mid);

    jmethodID dist_mid = (*env)->GetMethodID(env, options_cls, "getDist", "()Ljava/lang/String;");
    jstring dist = (jstring) (*env)->CallObjectMethod(env, sentry_sdk_options, dist_mid);

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

    if (release) {
        sentry_options_set_release(options, (*env)->GetStringUTFChars(env, release, 0));
    }
    if (environment) {
        sentry_options_set_environment(options, (*env)->GetStringUTFChars(env, environment, 0));
    }
    if (dist) {
        sentry_options_set_dist(options, (*env)->GetStringUTFChars(env, dist, 0));
    }
    // session tracking is enabled by default, but the Android SDK already handles it
    sentry_options_set_auto_session_tracking(options, 0);

    sentry_init(options);
}

JNIEXPORT void JNICALL
Java_io_sentry_android_ndk_NativeModuleListLoader_nativeClearModuleList(JNIEnv *env, jclass cls) {
    sentry_clear_modulecache();
}

JNIEXPORT jobjectArray JNICALL
Java_io_sentry_android_ndk_NativeModuleListLoader_nativeGetModuleList(JNIEnv *env, jclass cls) {
    sentry_value_t image_list_t = sentry_get_modules_list();
    jobjectArray image_list = NULL;

    if (sentry_value_get_type(image_list_t) == SENTRY_VALUE_TYPE_LIST) {
        size_t len_t = sentry_value_get_length(image_list_t);

        jclass image_class = (*env)->FindClass(env, "io/sentry/protocol/DebugImage");
        image_list = (*env)->NewObjectArray(env, len_t, image_class, NULL);

        jmethodID image_addr_method = (*env)->GetMethodID(env, image_class, "setImageAddr",
                                                          "(Ljava/lang/String;)V");

        jmethodID image_size_method = (*env)->GetMethodID(env, image_class, "setImageSize",
                                                          "(J)V");

        jmethodID code_file_method = (*env)->GetMethodID(env, image_class, "setCodeFile",
                                                         "(Ljava/lang/String;)V");

        jmethodID image_addr_ctor = (*env)->GetMethodID(env, image_class, "<init>",
                                                        "()V");

        jmethodID type_method = (*env)->GetMethodID(env, image_class, "setType",
                                                    "(Ljava/lang/String;)V");

        jmethodID debug_id_method = (*env)->GetMethodID(env, image_class, "setDebugId",
                                                        "(Ljava/lang/String;)V");

        jmethodID code_id_method = (*env)->GetMethodID(env, image_class, "setCodeId",
                                                       "(Ljava/lang/String;)V");

        jmethodID debug_file_method = (*env)->GetMethodID(env, image_class, "setDebugFile",
                                                          "(Ljava/lang/String;)V");

        for (size_t i = 0; i < len_t; i++) {
            sentry_value_t image_t = sentry_value_get_by_index(image_list_t, i);

            if (!sentry_value_is_null(image_t)) {
                jobject image = (*env)->NewObject(env, image_class, image_addr_ctor);

                sentry_value_t image_addr_t = sentry_value_get_by_key(image_t, "image_addr");
                if (!sentry_value_is_null(image_addr_t)) {

                    const char *value_v = sentry_value_as_string(image_addr_t);
                    jstring value = (*env)->NewStringUTF(env, value_v);

                    (*env)->CallVoidMethod(env, image, image_addr_method, value);
                }

                sentry_value_t image_size_t = sentry_value_get_by_key(image_t, "image_size");
                if (!sentry_value_is_null(image_size_t)) {

                    int32_t value_v = sentry_value_as_int32(image_size_t);
                    jlong value = (jlong) value_v;

                    (*env)->CallVoidMethod(env, image, image_size_method, value);
                }

                sentry_value_t code_file_t = sentry_value_get_by_key(image_t, "code_file");
                if (!sentry_value_is_null(code_file_t)) {

                    const char *value_v = sentry_value_as_string(code_file_t);
                    jstring value = (*env)->NewStringUTF(env, value_v);

                    (*env)->CallVoidMethod(env, image, code_file_method, value);
                }

                sentry_value_t code_type_t = sentry_value_get_by_key(image_t, "type");
                if (!sentry_value_is_null(code_type_t)) {

                    const char *value_v = sentry_value_as_string(code_type_t);
                    jstring value = (*env)->NewStringUTF(env, value_v);

                    (*env)->CallVoidMethod(env, image, type_method, value);
                }

                sentry_value_t debug_id_t = sentry_value_get_by_key(image_t, "debug_id");
                if (!sentry_value_is_null(code_type_t)) {

                    const char *value_v = sentry_value_as_string(debug_id_t);
                    jstring value = (*env)->NewStringUTF(env, value_v);

                    (*env)->CallVoidMethod(env, image, debug_id_method, value);
                }

                sentry_value_t code_id_t = sentry_value_get_by_key(image_t, "code_id");
                if (!sentry_value_is_null(code_id_t)) {

                    const char *value_v = sentry_value_as_string(code_id_t);
                    jstring value = (*env)->NewStringUTF(env, value_v);

                    (*env)->CallVoidMethod(env, image, code_id_method, value);
                }

                // not needed on Android, but keeping for forward compatibility
                sentry_value_t debug_file_t = sentry_value_get_by_key(image_t, "debug_file");
                if (!sentry_value_is_null(debug_file_t)) {

                    const char *value_v = sentry_value_as_string(debug_file_t);
                    jstring value = (*env)->NewStringUTF(env, value_v);

                    (*env)->CallVoidMethod(env, image, debug_file_method, value);
                }

                (*env)->SetObjectArrayElement(env, image_list, i, image);
            }
        }

        sentry_value_decref(image_list_t);
    }

    return image_list;
}
