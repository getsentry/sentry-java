#include <jni.h>
#include <atomic>
#include <memory>
#include <deque>
#include <vector>

namespace {

    int sApiLevel = android_get_device_api_level();

    struct Op {
        uint32_t type : 8;
        uint32_t skip : 24;
    };

    struct DisplayListData {
        void *vptr;
        uint8_t *fBytes;
        size_t fUsed;
        size_t fReserved;

        bool mHasText;
    };

    struct SkiaDisplayList {
        void *vptr;
        std::deque<void*> mChildNodes;
        std::deque<void*> mChildFunctors;
        std::vector<void*> mMutableImages;
        std::vector<void*> mMeshes;
        std::vector<void*> mVectorDrawables;
        bool mHasHolePunches;
        std::vector<void*> mAnimatedImages;
        DisplayListData mDisplayList;
//        std::deque<void*> mChildNodes;
//        std::deque<void*> mChildFunctors;
//        std::vector<void*> mMutableImages;
//        std::vector<const void*> mMeshes;
    };

    struct DisplayList {
        void *vptr;
        SkiaDisplayList* mImpl;
    };

    struct RenderNode {
        void *vptr;
        std::atomic<int32_t> mCount; // The only member of the RenderNodeHelper parent class VirtualLightRefBase.
        char* mName;
        void* mUserContext;

        uint32_t mDirtyPropertyFields;
        void* mProperties;
        void* mStagingProperties;

        bool mValid;

        bool mNeedsDisplayListSync;
        DisplayList mDisplayList;
        // ignored
    };

    struct RenderNode10 {
        void* vptr;
        std::atomic<int32_t> mCount; // The only member of the RenderNode parent class VirtualLightRefBase.
        int64_t mUniqueId;
        char* mName;
        void* mUserContext;

        uint32_t mDirtyPropertyFields;
        void* mProperties;
        void* mStagingProperties;

        bool mValid = false;

        bool mNeedsDisplayListSync;
        DisplayList mDisplayList;
        // ignored
    };


    DisplayListData *RenderNode_getNamePtr(jlong renderNode) {
        if (sApiLevel <= __ANDROID_API_P__) {
            auto node = reinterpret_cast<RenderNode *>(renderNode);
            auto displayList = node->mDisplayList;
            auto impl = reinterpret_cast<SkiaDisplayList *>(&displayList.mImpl);
            auto displayListData = &impl->mDisplayList;
            return displayListData;
        } else {
            auto node = reinterpret_cast<RenderNode10 *>(renderNode);
            auto displayList = node->mDisplayList;
            auto impl = reinterpret_cast<SkiaDisplayList *>(&displayList.mImpl);
            auto displayListData = &impl->mDisplayList;
            return displayListData;
        }
    }

//    DisplayListData *RenderNode_getName(jlong renderNode) {
//        return RenderNode_getNamePtr(renderNode);
//    }

    extern "C" JNIEXPORT void
    JNICALL
    Java_android_graphics_RenderNodeHelper_nGetDisplayList(JNIEnv
                                                           *env,
                                                           jclass clazz, jlong
                                                           render_node) {
        auto* displayList = RenderNode_getNamePtr(render_node);
        auto fBytes = displayList->fBytes;
        auto end = fBytes + displayList->fUsed;
        for (const uint8_t* ptr = fBytes; ptr < end;) {
            auto op = (const Op *) ptr;
            auto type = op->type;
            auto skip = op->skip;
            ptr += skip;
        }
//        auto test = name->mImpl->mDisplayList;
    }

}

