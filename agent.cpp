#include "jvmti.h"

#include <iostream>

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
    std::cout << "Hello, World!" << std::endl;

    return JNI_OK;
}

JNIEXPORT void JNICALL Agent_OnUnload(JavaVM *vm) {
    std::cout << "Goodbye, World!" << std::endl;
}