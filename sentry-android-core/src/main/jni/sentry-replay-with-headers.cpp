#include <jni.h>
#include <RenderNode.h>

namespace {
    extern "C" JNIEXPORT void
    JNICALL
    Java_android_graphics_RenderNodeHelper_nGetDisplayList2(JNIEnv *env,
                                                            jclass clazz,
                                                            jlong render_node) {
        auto node = reinterpret_cast<RenderNode *>(render_node);
    }
}
