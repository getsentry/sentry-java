#include <iostream>
#include "lib.h"

const std::string LEVEL_STRINGS[] = {
        "TRACE",
        "DEBUG",
        "INFO",
        "WARN",
        "ERROR"
};

Level LOG_LEVEL = WARN;

void log(Level level, std::string message) {
    if (level >= LOG_LEVEL) {
        std::cerr << LEVEL_STRINGS[level] << " [Sentry Agent]: " << message << std::endl;
    }
}

static jint throwException(JNIEnv *env, const char *name, const char *message) {
    jclass clazz;
    clazz = env->FindClass(name);
    return env->ThrowNew(clazz, message);
}

static jobject getLocalValue(jvmtiEnv* jvmti, JNIEnv *env, jthread thread, jint depth,
                             jvmtiLocalVariableEntry *table, int index) {
    jobject result;
    jint i_val;
    jfloat f_val;
    jdouble d_val;
    jlong j_val;
    jvmtiError jvmti_error;
    jclass reflect_class;
    jmethodID value_of;

    switch (table[index].signature[0]) {
        case '[': // Array
        case 'L': // Object
            jvmti_error = jvmti->GetLocalObject(thread, depth, table[index].slot, &result);
            if (jvmti_error != JVMTI_ERROR_NONE || result == nullptr) {
                return nullptr;
            }

            break;
        case 'J': // long
            jvmti_error = jvmti->GetLocalLong(thread, depth, table[index].slot, &j_val);
            break;
        case 'F': // float
            jvmti_error = jvmti->GetLocalFloat(thread, depth, table[index].slot, &f_val);
            break;
        case 'D': // double
            jvmti_error = jvmti->GetLocalDouble(thread, depth, table[index].slot, &d_val);
            break;
        case 'I': // int
        case 'S': // short
        case 'C': // char
        case 'B': // byte
        case 'Z': // boolean
            jvmti_error = jvmti->GetLocalInt(thread, depth, table[index].slot, &i_val);
            break;
            // error type
        default:
            return nullptr;
    }

    if (jvmti_error != JVMTI_ERROR_NONE) {
        return nullptr;
    }

    switch (table[index].signature[0]) {
        case 'J': // long
            reflect_class = env->FindClass("java/lang/Long");
            value_of = env->GetStaticMethodID(reflect_class, "valueOf", "(J)Ljava/lang/Long;");
            result = env->CallStaticObjectMethod(reflect_class, value_of, j_val);
            break;
        case 'F': // float
            reflect_class = env->FindClass("java/lang/Float");
            value_of = env->GetStaticMethodID(reflect_class, "valueOf", "(F)Ljava/lang/Float;");
            result = env->CallStaticObjectMethod(reflect_class, value_of, f_val);
            break;
        case 'D': // double
            reflect_class = env->FindClass("java/lang/Double");
            value_of = env->GetStaticMethodID(reflect_class, "valueOf", "(D)Ljava/lang/Double;");
            result = env->CallStaticObjectMethod(reflect_class, value_of, d_val);
            break;
            // INTEGER TYPES
        case 'I': // int
            reflect_class = env->FindClass("java/lang/Integer");
            value_of = env->GetStaticMethodID(reflect_class, "valueOf", "(I)Ljava/lang/Integer;");
            result = env->CallStaticObjectMethod(reflect_class, value_of, i_val);
            break;
        case 'S': // short
            reflect_class = env->FindClass("java/lang/Short");
            value_of = env->GetStaticMethodID(reflect_class, "valueOf", "(S)Ljava/lang/Short;");
            result = env->CallStaticObjectMethod(reflect_class, value_of, i_val);
            break;
        case 'C': // char
            reflect_class = env->FindClass("java/lang/Character");
            value_of = env->GetStaticMethodID(reflect_class, "valueOf", "(C)Ljava/lang/Character;");
            result = env->CallStaticObjectMethod(reflect_class, value_of, i_val);
            break;
        case 'B': // byte
            reflect_class = env->FindClass("java/lang/Byte");
            value_of = env->GetStaticMethodID(reflect_class, "valueOf", "(B)Ljava/lang/Byte;");
            result = env->CallStaticObjectMethod(reflect_class, value_of, i_val);
            break;
        case 'Z': // boolean
            reflect_class = env->FindClass("java/lang/Boolean");
            value_of = env->GetStaticMethodID(reflect_class, "valueOf", "(Z)Ljava/lang/Boolean;");
            result = env->CallStaticObjectMethod(reflect_class, value_of, i_val);
            break;
        default:  // jobject
            break;
    }

    return result;
}

static void makeLocalVariable(jvmtiEnv* jvmti, JNIEnv *env, jthread thread,
                              jint depth, jclass local_class, jmethodID live_ctor,
                              jlocation location, jobjectArray locals,
                              jvmtiLocalVariableEntry *table, int index) {
    jstring name;
    jobject value;
    jobject local;

    name = env->NewStringUTF(table[index].name);

    if (location >= table[index].start_location && location <= (table[index].start_location + table[index].length)) {
        value = getLocalValue(jvmti, env, thread, depth, table, index);
        local = env->NewObject(local_class, live_ctor, name, value);
    } else {
        // dead object, use null
        local = nullptr;
    }

    env->SetObjectArrayElement(locals, index, local);
}

static jobject makeFrameObject(jvmtiEnv* jvmti, JNIEnv *env, jmethodID method, jobjectArray locals) {
    jvmtiError jvmti_error;
    jclass method_class;
    jobject frame_method;
    jclass frame_class;
    jmethodID ctor;

    jvmti_error = jvmti->GetMethodDeclaringClass(method, &method_class);
    if (jvmti_error != JVMTI_ERROR_NONE) {
        throwException(env, "java/lang/RuntimeException", "Could not get the declaring class of the method.");
        return nullptr;
    }

    frame_method = env->ToReflectedMethod(method_class, method, (jboolean) true);
    if (frame_method == nullptr) {
        return nullptr; // ToReflectedMethod raised an exception
    }

    frame_class = env->FindClass("io/sentry/jvmti/Frame");
    if (frame_class == nullptr) {
        return nullptr;
    }

    ctor = env->GetMethodID(frame_class, "<init>",
                            "(Ljava/lang/reflect/Method;[Lio/sentry/jvmti/Frame$LocalVariable;)V");
    if (ctor == nullptr) {
        return nullptr;
    }

    return env->NewObject(frame_class, ctor, frame_method, locals);
}

static jobject buildFrame(jvmtiEnv* jvmti, JNIEnv *env, jthread thread, jint depth,
                          jmethodID method, jlocation location) {
    jvmtiError jvmti_error;
    jvmtiLocalVariableEntry *local_var_table;
    jint num_entries;
    jobject value_ptr;
    jobjectArray locals;
    jclass local_class;
    jmethodID live_ctor;
    int i;
    value_ptr = nullptr;

    jvmti_error = jvmti->GetLocalVariableTable(method, &num_entries, &local_var_table);
    if (jvmti_error != JVMTI_ERROR_NONE) {
        locals = nullptr;
        switch(jvmti_error) {
            // Pass cases
            case JVMTI_ERROR_ABSENT_INFORMATION:
            case JVMTI_ERROR_NATIVE_METHOD:
                break;
                // Error cases
            case JVMTI_ERROR_MUST_POSSESS_CAPABILITY:
                throwException(env, "java/lang/RuntimeException", "The access_local_variables capability is not enabled.");
                return nullptr;
            case JVMTI_ERROR_INVALID_METHODID:
                throwException(env, "java/lang/IllegalArgumentException", "Illegal jmethodID.");
                return nullptr;
            case JVMTI_ERROR_NULL_POINTER:
                throwException(env, "java/lang/NullPointerException", "Passed null to GetLocalVariableTable().");
                return nullptr;
            default:
                throwException(env, "java/lang/RuntimeException", "Unknown JVMTI Error.");
                return nullptr;
        }
    } else {
        local_class = env->FindClass("io/sentry/jvmti/Frame$LocalVariable");
        live_ctor = env->GetMethodID(local_class, "<init>", "(Ljava/lang/String;Ljava/lang/Object;)V");
        locals = env->NewObjectArray(num_entries, local_class, nullptr);
        for (i = 0; i < num_entries; i++) {
            makeLocalVariable(jvmti, env, thread, depth, local_class, live_ctor, location, locals, local_var_table, i);
        }
        jvmti->Deallocate((unsigned char *) local_var_table);
    }

    jvmti_error = jvmti->GetLocalObject(thread, depth, 0, &value_ptr);
    if (jvmti_error != JVMTI_ERROR_NONE) {
        value_ptr = nullptr;
    }

    return makeFrameObject(jvmti, env, method, locals);
}

jobjectArray buildStackTraceFrames(jvmtiEnv* jvmti, JNIEnv *env, jthread thread,
                                   jint start_depth, jint num_frames) {
    log(TRACE, "buildStackTraceFrames called.");

    jvmtiFrameInfo* frames;
    jclass result_class;
    jint num_frames_returned;
    jvmtiError jvmti_error;
    jobjectArray result;
    jobject frame;

    jvmti_error = jvmti->Allocate(num_frames * (int)sizeof(jvmtiFrameInfo), (unsigned char **) &frames);
    if (jvmti_error != JVMTI_ERROR_NONE) {
        throwException(env, "java/lang/RuntimeException", "Could not allocate frame buffer.");
        return nullptr;
    }

    jvmti_error = jvmti->GetStackTrace(thread, start_depth, num_frames, frames, &num_frames_returned);
    if (jvmti_error != JVMTI_ERROR_NONE) {
        jvmti->Deallocate((unsigned char *)frames);
        throwException(env, "java/lang/RuntimeException", "Could not get stack trace.");
        return nullptr;
    }

    result_class = env->FindClass("io/sentry/jvmti/Frame");
    result = env->NewObjectArray(num_frames_returned, result_class, nullptr);
    if (result == nullptr) {
        jvmti->Deallocate((unsigned char *) frames);
        return nullptr; // OutOfMemory
    }

    for (int i = 0; i < num_frames_returned; i++) {
        frame = buildFrame(jvmti, env, thread, start_depth + i, frames[i].method, frames[i].location);
        if (frame == nullptr) {
            jvmti->Deallocate((unsigned char *) frames);
            throwException(env, "java/lang/RuntimeException", "Error accessing frame object.");
            return nullptr;
        }
        env->SetObjectArrayElement(result, i, frame);
    }

    jvmti->Deallocate((unsigned char *) frames);

    log(TRACE, "buildStackTraceFrames exit.");
    return result;
}
