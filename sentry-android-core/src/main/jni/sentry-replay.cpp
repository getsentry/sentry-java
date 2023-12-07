#include <jni.h>
#include <atomic>
#include <memory>
#include <deque>
#include <vector>

namespace {

    int sApiLevel = android_get_device_api_level();
    typedef uint32_t SkColor;
    typedef float SkScalar;

    struct SkV3 {
        float x, y, z;
    };

    struct SkM44 {
        SkScalar fMat[16];
    };

    struct SkMatrix {
        enum TypeMask {
            kIdentity_Mask    = 0,    //!< identity SkMatrix; all bits clear
            kTranslate_Mask   = 0x01, //!< translation SkMatrix
            kScale_Mask       = 0x02, //!< scale SkMatrix
            kAffine_Mask      = 0x04, //!< skew or rotate SkMatrix
            kPerspective_Mask = 0x08, //!< perspective SkMatrix
        };

        static constexpr int kMScaleX = 0; //!< horizontal scale factor
        static constexpr int kMSkewX  = 1; //!< horizontal skew factor
        static constexpr int kMTransX = 2; //!< horizontal translation
        static constexpr int kMSkewY  = 3; //!< vertical skew factor
        static constexpr int kMScaleY = 4; //!< vertical scale factor
        static constexpr int kMTransY = 5; //!< vertical translation
        static constexpr int kMPersp0 = 6; //!< input x perspective factor
        static constexpr int kMPersp1 = 7; //!< input y perspective factor
        static constexpr int kMPersp2 = 8; //!< perspective bias

        /** Affine arrays are in column-major order to match the matrix used by
            PDF and XPS.
        */
        static constexpr int kAScaleX = 0; //!< horizontal scale factor
        static constexpr int kASkewY  = 1; //!< vertical skew factor
        static constexpr int kASkewX  = 2; //!< horizontal skew factor
        static constexpr int kAScaleY = 3; //!< vertical scale factor
        static constexpr int kATransX = 4; //!< horizontal translation
        static constexpr int kATransY = 5; //!< vertical translation

        static constexpr int kRectStaysRect_Mask = 0x10;

        /** Set if the perspective bit is valid even though the rest of
            the matrix is Unknown.
        */
        static constexpr int kOnlyPerspectiveValid_Mask = 0x40;

        static constexpr int kUnknown_Mask = 0x80;

        static constexpr int kORableMasks = kTranslate_Mask |
                                            kScale_Mask |
                                            kAffine_Mask |
                                            kPerspective_Mask;

        static constexpr int kAllMasks = kTranslate_Mask |
                                         kScale_Mask |
                                         kAffine_Mask |
                                         kPerspective_Mask |
                                         kRectStaysRect_Mask;

        SkScalar        fMat[9];
        mutable int32_t fTypeMask;

    };

    template <typename F, typename S>
    struct Pair {
        F first;
        S second;

        Pair() {}
        Pair(const Pair& o) : first(o.first), second(o.second) {}
        Pair(const F& f, const S& s) : first(f), second(s) {}

        inline const F& getFirst() const { return first; }

        inline const S& getSecond() const { return second; }
    };

    struct SkCamera3D {
        SkV3   fLocation;   // origin of the camera's space
        SkV3   fAxis;       // view direction
        SkV3   fZenith;     // up direction
        SkV3   fObserver;   // eye position (may not be the same as the origin)
        mutable SkMatrix    fOrientation;
        mutable bool        fNeedToUpdate;
    };

    struct Sk3DView {
        struct Rec {
            Rec *fNext;
            SkM44 fMatrix;
        };
        Rec *fRec;
        Rec fInitialRec;
        SkCamera3D fCamera;
    };

    enum SkPathSegmentMask {
        kLine_SkPathSegmentMask   = 1 << 0,
        kQuad_SkPathSegmentMask   = 1 << 1,
        kConic_SkPathSegmentMask  = 1 << 2,
        kCubic_SkPathSegmentMask  = 1 << 3,
    };

    enum class SkPathVerb {
        kMove,   //!< SkPath::RawIter returns 1 point
        kLine,   //!< SkPath::RawIter returns 2 points
        kQuad,   //!< SkPath::RawIter returns 3 points
        kConic,  //!< SkPath::RawIter returns 3 points + 1 weight
        kCubic,  //!< SkPath::RawIter returns 4 points
        kClose   //!< SkPath::RawIter returns 0 points
    };

    struct SkPoint {
        int32_t fX; //!< x-axis value
        int32_t fY; //!< y-axis value
    };
    struct SkPath {
        enum ArcSize {
            kSmall_ArcSize, //!< smaller of arc pair
            kLarge_ArcSize, //!< larger of arc pair
        };
        enum AddPathMode {
            kAppend_AddPathMode, //!< appended to destination unaltered
            kExtend_AddPathMode, //!< add line if prior contour is not closed
        };
        enum SegmentMask {
            kLine_SegmentMask  = kLine_SkPathSegmentMask,
            kQuad_SegmentMask  = kQuad_SkPathSegmentMask,
            kConic_SegmentMask = kConic_SkPathSegmentMask,
            kCubic_SegmentMask = kCubic_SkPathSegmentMask,
        };
        enum Verb {
            kMove_Verb  = static_cast<int>(SkPathVerb::kMove),
            kLine_Verb  = static_cast<int>(SkPathVerb::kLine),
            kQuad_Verb  = static_cast<int>(SkPathVerb::kQuad),
            kConic_Verb = static_cast<int>(SkPathVerb::kConic),
            kCubic_Verb = static_cast<int>(SkPathVerb::kCubic),
            kClose_Verb = static_cast<int>(SkPathVerb::kClose),
            kDone_Verb  = kClose_Verb + 1
        };

        struct Iter {
            const void*  fPts;
            const void*  fVerbs;
            const void*  fVerbStop;
            const void* fConicWeights;
            SkPoint         fMoveTo;
            SkPoint         fLastPt;
            bool            fForceClose;
            bool            fNeedClose;
            bool            fCloseLine;
        };
        struct RangeIter {
            const void* fVerb;
            const void* fPoints;
            const void* fWeights;
        };
        struct RawIter {
            RangeIter fIter;
            RangeIter fEnd;
            float fConicWeight = 0;
        };
        void*               fPathRef;
        int                            fLastMoveToIndex;
        mutable std::atomic<uint8_t>   fConvexity;      // SkPathConvexity
        mutable std::atomic<uint8_t>   fFirstDirection; // SkPathFirstDirection
        uint8_t                        fFillType    : 2;
        uint8_t                        fIsVolatile  : 1;
    };

    struct Rect {
        float left;
        float top;
        float right;
        float bottom;
        typedef float value_type;
    };

    struct RevealClip {
        bool mShouldClip;
        float mX;
        float mY;
        float mRadius;
        SkPath mPath;
    };

    struct Outline {
        enum class Type {
            None = 0, Empty = 1, Path = 2, RoundRect = 3
        };
        bool mShouldClip;
        Type mType;
        Rect mBounds;
        float mRadius;
        float mAlpha;
        SkPath mPath;
    };

    enum class SkBlendMode {
        kClear,         //!< r = 0
        kSrc,           //!< r = s
        kDst,           //!< r = d
        kSrcOver,       //!< r = s + (1-sa)*d
        kDstOver,       //!< r = d + (1-da)*s
        kSrcIn,         //!< r = s * da
        kDstIn,         //!< r = d * sa
        kSrcOut,        //!< r = s * (1-da)
        kDstOut,        //!< r = d * (1-sa)
        kSrcATop,       //!< r = s*da + d*(1-sa)
        kDstATop,       //!< r = d*sa + s*(1-da)
        kXor,           //!< r = s*(1-da) + d*(1-sa)
        kPlus,          //!< r = min(s + d, 1)
        kModulate,      //!< r = s*d
        kScreen,        //!< r = s + d - s*d

        kOverlay,       //!< multiply or screen, depending on destination
        kDarken,        //!< rc = s + d - max(s*da, d*sa), ra = kSrcOver
        kLighten,       //!< rc = s + d - min(s*da, d*sa), ra = kSrcOver
        kColorDodge,    //!< brighten destination to reflect source
        kColorBurn,     //!< darken destination to reflect source
        kHardLight,     //!< multiply or screen, depending on source
        kSoftLight,     //!< lighten or darken, depending on source
        kDifference,    //!< rc = s + d - 2*(min(s*da, d*sa)), ra = kSrcOver
        kExclusion,     //!< rc = s + d - two(s*d), ra = kSrcOver
        kMultiply,      //!< r = s*(1-da) + d*(1-sa) + s*d

        kHue,           //!< hue of source with saturation and luminosity of destination
        kSaturation,    //!< saturation of source with hue and luminosity of destination
        kColor,         //!< hue and saturation of source with luminosity of destination
        kLuminosity,    //!< luminosity of source with hue and saturation of destination

        kLastCoeffMode = kScreen,     //!< last porter duff blend mode
        kLastSeparableMode = kMultiply,   //!< last blend mode operating separately on components
        kLastMode = kLuminosity, //!< last valid value
    };


    enum class LayerType {
        None = 0,
        // We cannot build the software layer directly (must be done at record time) and all management
        // of software layers is handled in Java.
        Software = 1,
        RenderLayer = 2,
    };

    struct SkVector {
        float x;
        float y;
    };

    struct StretchEffect {
        float NON_ZERO_EPSILON = 0.00004f;
        mutable SkVector mStretchDirection{0, 0};
        void *mBuilder;
    };

    struct LayerProperties {
        LayerType mType = LayerType::None;
        // Whether or not that Layer's content is opaque, doesn't include alpha
        bool mOpaque;
        uint8_t mAlpha;
        SkBlendMode mMode;
        void *mColorFilter;
        void *mImageFilter;
        StretchEffect mStretchEffect;
    };


    struct RenderProperties {
        void *vptr;
        struct PrimitiveFields {
            int mLeft = 0, mTop = 0, mRight = 0, mBottom = 0;
            int mWidth = 0, mHeight = 0;
            int mClippingFlags = 0;
            SkColor mSpotShadowColor = 0;
            SkColor mAmbientShadowColor = 0;
            float mAlpha = 1;
            float mTranslationX = 0, mTranslationY = 0, mTranslationZ = 0;
            float mElevation = 0;
            float mRotation = 0, mRotationX = 0, mRotationY = 0;
            float mScaleX = 1, mScaleY = 1;
            float mPivotX = 0, mPivotY = 0;
            bool mHasOverlappingRendering = false;
            bool mPivotExplicitlySet = false;
            bool mMatrixOrPivotDirty = false;
            bool mProjectBackwards = false;
            bool mProjectionReceiver = false;
            bool mAllowForceDark = true;
            bool mClipMayBeComplex = false;
            Rect mClipBounds;
            Outline mOutline;
            RevealClip mRevealClip;
        } mPrimitiveFields;

        void *mStaticMatrix;
        void *mAnimationMatrix;
        LayerProperties mLayerProperties;

        /**
         * These fields are all generated from other properties and are not set directly.
         */
        struct ComputedFields {
            /**
             * Stores the total transformation of the DisplayList based upon its scalar
             * translate/rotate/scale properties.
             *
             * In the common translation-only case, the matrix isn't necessarily allocated,
             * and the mTranslation properties are used directly.
             */
            void *mTransformMatrix;

            Sk3DView mTransformCamera;

            // Force layer on for functors to enable render features they don't yet support (clipping)
            bool mNeedLayerForFunctors = false;
        } mComputedFields;
    };

    struct RenderNodeDrawable {
        void* vptr;
        std::atomic<int32_t> fRefCnt;
        int32_t fGenerationId;
        void* renderNode;
        const SkMatrix mRecordedTransform;
        const bool mComposeLayer;
        bool mInReorderingSection;
        void* mProjectedDisplayList;
    };

    template <typename T,
            typename = std::enable_if_t<std::is_trivially_default_constructible<T>::value &&
                                        std::is_trivially_destructible<T>::value>>
    class AutoTMalloc {
    public:
        /** Takes ownership of the ptr. The ptr must be a value which can be passed to std::free. */
        explicit AutoTMalloc(T* ptr = nullptr) : fPtr(ptr) {}

        /** Allocates space for 'count' Ts. */
        explicit AutoTMalloc(size_t count) : fPtr(mallocIfCountThrowOnFail(count)) {}

        AutoTMalloc(AutoTMalloc&&) = default;
        AutoTMalloc& operator=(AutoTMalloc&&) = default;

        /** Resize the memory area pointed to by the current ptr preserving contents. */
        void realloc(size_t count) { fPtr.reset(reallocIfCountThrowOnFail(count)); }

        /** Resize the memory area pointed to by the current ptr without preserving contents. */
        T* reset(size_t count = 0) {
            fPtr.reset(mallocIfCountThrowOnFail(count));
            return this->get();
        }

        T* get() const { return fPtr.get(); }

        operator T*() { return fPtr.get(); }

        operator const T*() const { return fPtr.get(); }

        T& operator[](int index) { return fPtr.get()[index]; }

        const T& operator[](int index) const { return fPtr.get()[index]; }

        /**
         *  Transfer ownership of the ptr to the caller, setting the internal
         *  pointer to NULL. Note that this differs from get(), which also returns
         *  the pointer, but it does not transfer ownership.
         */
        T* release() { return fPtr.release(); }

    private:
        struct FreeDeleter {
            void operator()(uint8_t* p) { std::free(p); }
        };
        std::unique_ptr<T, FreeDeleter> fPtr;

        T* mallocIfCountThrowOnFail(size_t count) {
            T* newPtr = nullptr;
            if (count) {
                newPtr = (T*)std::malloc(count * sizeof(T));
            }
            return newPtr;
        }
        T* reallocIfCountThrowOnFail(size_t count) {
            T* newPtr = nullptr;
            if (count) {
                newPtr = (T*)std::realloc(fPtr.release(), count * sizeof(T));
            }
            return newPtr;
        }
    };

    struct Op {
        uint8_t type: 8;
        uint32_t skip: 24;
    };

    struct DisplayListData {
        uint8_t* fBytes;
        size_t fUsed;
        size_t fReserved;

        bool mHasText;
    };

    struct LinearAllocator {
        class Page;
        typedef void (*Destructor)(void* addr);
        struct DestructorNode {
            Destructor dtor;
            void* addr;
            void* next = nullptr;
        };

        size_t mPageSize;
        size_t mMaxAllocSize;
        void* mNext;
        void* mCurrentPage;
        void* mPages;
        void* mDtorList = nullptr;

        // Memory usage tracking
        size_t mTotalAllocated;
        size_t mWastedSpace;
        size_t mPageCount;
        size_t mDedicatedPageCount;
    };

    struct SkiaDisplayList {
        LinearAllocator allocator;
        std::deque<RenderNodeDrawable> mChildNodes;
        std::deque<void*> mChildFunctors;
        std::vector<void *> mMutableImages;
        std::vector<void *> mMeshes;
        std::vector<Pair<void*, SkMatrix>> mVectorDrawables;
        bool mHasHolePunches;
        std::vector<void *> mAnimatedImages;
        DisplayListData mDisplayList;
//        std::deque<void*> mChildNodes;
//        std::deque<void*> mChildFunctors;
//        std::vector<void*> mMutableImages;
//        std::vector<const void*> mMeshes;
    };

    struct DisplayList {
        SkiaDisplayList *mImpl;
    };

    struct RenderNode {
        void *vptr;
        std::atomic<int32_t> mCount; // The only member of the RenderNodeHelper parent class VirtualLightRefBase.
        char *mName;
        void *mUserContext;

        uint32_t mDirtyPropertyFields;
        RenderProperties mProperties;
        RenderProperties mStagingProperties;

        bool mValid;

        bool mNeedsDisplayListSync;
        DisplayList mDisplayList;
        // ignored
    };

    struct RenderNode10 {
        void *vptr;
        std::atomic<int32_t> mCount; // The only member of the RenderNode parent class VirtualLightRefBase.
        int64_t mUniqueId;
        char *mName;
        void *mUserContext;

        uint32_t mDirtyPropertyFields;
        RenderProperties mProperties;
        RenderProperties mStagingProperties;

        bool mValid;

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
            auto impl = displayList.mImpl;
            for (int i = 0; i < impl->mChildNodes.size(); ++i) {
                void* nodePtr = impl->mChildNodes[i].renderNode;
                auto childNode = reinterpret_cast<RenderNode10 *>(nodePtr);
            }
            auto displayListData = &impl->mDisplayList;
            return displayListData;
        }
    }

//    DisplayListData *RenderNode_getName(jlong renderNode) {
//        return RenderNode_getNamePtr(renderNode);
//    }

    extern "C" JNIEXPORT jobject
    JNICALL
    Java_android_graphics_RenderNodeHelper_nGetDisplayList(JNIEnv
                                                           *env,
                                                           jclass clazz, jlong
                                                           render_node) {
        auto node = reinterpret_cast<RenderNode10 *>(render_node);
        auto* displayList = node->mDisplayList.mImpl;
        auto data = &displayList->mDisplayList;
//        auto renderProperties = &node->properties();
//        auto test = name->mImpl->mDisplayList;

        auto end = data->fBytes + data->fUsed;
        for (const uint8_t* ptr = data->fBytes; ptr < end;) {
            auto op = (const Op *) ptr;
            auto type = op->type;
            auto skip = op->skip;
            ptr += skip;
        }
        return nullptr;
    }

}

