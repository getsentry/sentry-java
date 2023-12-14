#include <jni.h>
#include <stdlib.h>
#include <RenderNode.h>
#include <SkTextBlob.h>
#include <SkPaint.h>
#include <SkTextBlobPriv.h>
#include <SkTypes.h>
#include <SkGlyph.h>
#include <SkGlyphRun.h>
//#include <RecordingCanvas.h>
//#include <Rect.h>
#include <shadowhook.h>

namespace {
//    static const SkRect kUnset = {SK_ScalarInfinity, 0, 0, 0};
//    static const SkRect* maybe_unset(const SkRect& r) {
//        return r.left() == SK_ScalarInfinity ? nullptr : &r;
//    }
//

    void *DisplayListData_draw_hook = nullptr;
    void *DumpOpsCanvas_onDrawTextBlob_hook = nullptr;
    void *SkCanvas_onDrawTextBlob_hook = nullptr;

    struct Op {
        uint32_t type: 8;
        uint32_t skip: 24;
    };
    static_assert(sizeof(Op) == 4, "");
//
//
    struct Flush final : Op {
        static const auto kType = android::uirenderer::DisplayListOpType::Flush;
    };
//
    struct Save final : Op {
        static const auto kType = android::uirenderer::DisplayListOpType::Save;
    };
    struct Restore final : Op {
        static const auto kType = android::uirenderer::DisplayListOpType::Restore;
    };

//    struct SaveLayer final : Op {
//        static const auto kType = android::uirenderer::DisplayListOpType::SaveLayer;
//        SaveLayer(const SkRect* bounds, const SkPaint* paint, const SkImageFilter* backdrop,
//                  SkCanvas::SaveLayerFlags flags) {
//            if (bounds) {
//                this->bounds = *bounds;
//            }
//            if (paint) {
//                this->paint = *paint;
//            }
//            this->backdrop = sk_ref_sp(backdrop);
//            this->flags = flags;
//        }
//        SkRect bounds = kUnset;
//        SkPaint paint;
//        sk_sp<const SkImageFilter> backdrop;
//        SkCanvas::SaveLayerFlags flags;
//        void draw(SkCanvas* c, const SkMatrix&) const {
//            c->saveLayer({maybe_unset(bounds), &paint, backdrop.get(), flags});
//        }
//    };
//    struct SaveBehind final : Op {
//        static const auto kType = android::uirenderer::DisplayListOpType::SaveBehind;
//        SaveBehind(const SkRect* subset) {
//            if (subset) { this->subset = *subset; }
//        }
//        SkRect  subset = kUnset;
//        void draw(SkCanvas* c, const SkMatrix&) const {
////            SkAndroidFrameworkUtils::SaveBehind(c, &subset);
//        }
//    };
//
//    struct Concat final : Op {
//        static const auto kType = android::uirenderer::DisplayListOpType::Concat;
//        Concat(const SkM44& matrix) : matrix(matrix) {}
//        SkM44 matrix;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->concat(matrix); }
//    };
//    struct SetMatrix final : Op {
//        static const auto kType = android::uirenderer::DisplayListOpType::SetMatrix;
//        SetMatrix(const SkM44& matrix) : matrix(matrix) {}
//        SkM44 matrix;
//        void draw(SkCanvas* c, const SkMatrix& original) const {
//            c->setMatrix(SkM44(original) * matrix);
//        }
//    };
//    struct Scale final : Op {
//        static const auto kType = android::uirenderer::DisplayListOpType::Scale;
//        Scale(SkScalar sx, SkScalar sy) : sx(sx), sy(sy) {}
//        SkScalar sx, sy;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->scale(sx, sy); }
//    };
    struct Translate final : Op {
        static const auto kType = android::uirenderer::DisplayListOpType::Translate;

        Translate(SkScalar dx, SkScalar dy) : dx(dx), dy(dy) {}

        SkScalar dx, dy;
    };

//
//    struct ClipPath final : Op {
//        static const auto kType = android::uirenderer::DisplayListOpType::ClipPath;
//        ClipPath(const SkPath& path, SkClipandroid::uirenderer::DisplayListOp op, bool aa) : path(path), op(op), aa(aa) {}
//        SkPath path;
//        SkClipandroid::uirenderer::DisplayListOp op;
//        bool aa;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->clipPath(path, op, aa); }
//    };
    struct ClipRect final : Op {
        static const auto kType = android::uirenderer::DisplayListOpType::ClipRect;

        ClipRect(const SkRect &rect, SkClipOp op, bool aa) : rect(rect), op(op), aa(aa) {}

        SkRect rect;
        SkClipOp op;
        bool aa;

        void draw(SkCanvas *c, const SkMatrix &) const { c->clipRect(rect, op, aa); }
    };

//    struct ClipRRect final : Op {
//        static const auto kType = android::uirenderer::DisplayListOpType::ClipRRect;
//        ClipRRect(const SkRRect& rrect, SkClipandroid::uirenderer::DisplayListOp op, bool aa) : rrect(rrect), op(op), aa(aa) {}
//        SkRRect rrect;
//        SkClipandroid::uirenderer::DisplayListOp op;
//        bool aa;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->clipRRect(rrect, op, aa); }
//    };
//    struct ClipRegion final : Op {
//        static const auto kType = android::uirenderer::DisplayListOpType::ClipRegion;
//        ClipRegion(const SkRegion& region, SkClipandroid::uirenderer::DisplayListOp op) : region(region), op(op) {}
//        SkRegion region;
//        SkClipandroid::uirenderer::DisplayListOp op;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->clipRegion(region, op); }
//    };
//    struct ResetClip final : Op {
//        static const auto kType = android::uirenderer::DisplayListOpType::ResetClip;
//        ResetClip() {}
//        void draw(SkCanvas* c, const SkMatrix&) const {
////            SkAndroidFrameworkUtils::ResetClip(c);
//        }
//    };
//
//    struct DrawPaint final : Op {
//        static const auto kType = android::uirenderer::DisplayListOpType::DrawPaint;
//        DrawPaint(const SkPaint& paint) : paint(paint) {}
//        SkPaint paint;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->drawPaint(paint); }
//    };
//    struct DrawBehind final : Op {
//        static const auto kType = android::uirenderer::DisplayListOpType::DrawBehind;
//        DrawBehind(const SkPaint& paint) : paint(paint) {}
//        SkPaint paint;
//        void draw(SkCanvas* c, const SkMatrix&) const {
////            SkCanvasPriv::DrawBehind(c, paint);
//            }
//    };
//    struct DrawPath final : Op {
//        static const auto kType = android::uirenderer::DisplayListOpType::DrawPath;
//        DrawPath(const SkPath& path, const SkPaint& paint) : path(path), paint(paint) {}
//        SkPath path;
//        SkPaint paint;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->drawPath(path, paint); }
//    };
    struct DrawRect final : Op {
        static const auto kType = android::uirenderer::DisplayListOpType::DrawRect;

        DrawRect(const SkRect &rect, const SkPaint &paint) : rect(rect), paint(paint) {}

        SkRect rect;
        SkPaint paint;

        void draw(SkCanvas *c, const SkMatrix &) const { c->drawRect(rect, paint); }
    };

//    struct DrawRegion final : Op {
//        static const auto kType = android::uirenderer::DisplayListOpType::DrawRegion;
//        DrawRegion(const SkRegion& region, const SkPaint& paint) : region(region), paint(paint) {}
//        SkRegion region;
//        SkPaint paint;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->drawRegion(region, paint); }
//    };
//    struct DrawOval final : Op {
//        static const auto kType = android::uirenderer::DisplayListOpType::DrawOval;
//        DrawOval(const SkRect& oval, const SkPaint& paint) : oval(oval), paint(paint) {}
//        SkRect oval;
//        SkPaint paint;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->drawOval(oval, paint); }
//    };
//    struct DrawArc final : Op {
//        static const auto kType = android::uirenderer::DisplayListOpType::DrawArc;
//        DrawArc(const SkRect& oval, SkScalar startAngle, SkScalar sweepAngle, bool useCenter,
//                const SkPaint& paint)
//                : oval(oval)
//                , startAngle(startAngle)
//                , sweepAngle(sweepAngle)
//                , useCenter(useCenter)
//                , paint(paint) {}
//        SkRect oval;
//        SkScalar startAngle;
//        SkScalar sweepAngle;
//        bool useCenter;
//        SkPaint paint;
//        void draw(SkCanvas* c, const SkMatrix&) const {
//            c->drawArc(oval, startAngle, sweepAngle, useCenter, paint);
//        }
//    };
//    struct DrawRRect final : Op {
//        static const auto kType = android::uirenderer::DisplayListOpType::DrawRRect;
//        DrawRRect(const SkRRect& rrect, const SkPaint& paint) : rrect(rrect), paint(paint) {}
//        SkRRect rrect;
//        SkPaint paint;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->drawRRect(rrect, paint); }
//    };
//    struct DrawDRRect final : Op {
//        static const auto kType = android::uirenderer::DisplayListOpType::DrawDRRect;
//        DrawDRRect(const SkRRect& outer, const SkRRect& inner, const SkPaint& paint)
//                : outer(outer), inner(inner), paint(paint) {}
//        SkRRect outer, inner;
//        SkPaint paint;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->drawDRRect(outer, inner, paint); }
//    };
//    struct DrawDrawable final : Op {
//        static const auto kType = android::uirenderer::DisplayListOpType::DrawDrawable;
//        DrawDrawable(SkDrawable* drawable, const SkMatrix* matrix) : drawable(sk_ref_sp(drawable)) {
//            if (matrix) {
//                this->matrix = *matrix;
//            }
//        }
//        sk_sp<SkDrawable> drawable;
//        SkMatrix matrix = SkMatrix::I();
//        // It is important that we call drawable->draw(c) here instead of c->drawDrawable(drawable).
//        // Drawables are mutable and in cases, like RenderNodeDrawable, are not expected to produce the
//        // same content if retained outside the duration of the frame. Therefore we resolve
//        // them now and do not allow the canvas to take a reference to the drawable and potentially
//        // keep it alive for longer than the frames duration (e.g. SKP serialization).
//        void draw(SkCanvas* c, const SkMatrix&) const { drawable->draw(c, &matrix); }
//    };
    enum class DrawTextBlobMode {
        Normal,
        HctOutline,
        HctInner,
    };

    struct DrawTextBlob final : Op {
        static const auto kType = android::uirenderer::DisplayListOpType::DrawTextBlob;
        sk_sp<const SkTextBlob> blob;
        SkScalar x, y;
        SkPaint paint;
        DrawTextBlobMode drawTextBlobMode;

        void draw(SkCanvas *c, const SkMatrix &) const { c->drawTextBlob(blob.get(), x, y, paint); }
    };
//


    jobject getProperties(JNIEnv *env, const char *op, jobject args) {
        jclass hashMapClass = env->FindClass("java/util/HashMap");
        jmethodID hashMapConstructor = env->GetMethodID(hashMapClass, "<init>",
                                                        "(I)V");
        jobject hashMap = env->NewObject(hashMapClass, hashMapConstructor, 2);

        jmethodID hashMapPut = env->GetMethodID(hashMapClass, "put",
                                                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

        env->CallObjectMethod(hashMap, hashMapPut, env->NewStringUTF("property"),
                              env->NewStringUTF(op));
        env->CallObjectMethod(hashMap, hashMapPut, env->NewStringUTF("args"), args);
        return hashMap;
    }

    jobject newInt(JNIEnv *env, int value) {
        jobject newInt = env->CallStaticObjectMethod(
                env->FindClass("java/lang/Integer"),
                env->GetStaticMethodID(env->FindClass("java/lang/Integer"), "valueOf",
                                       "(I)Ljava/lang/Integer;"),
                value
        );
        return newInt;
    }

    jobject newFloat(JNIEnv *env, float value) {
        jobject newFloat = env->CallStaticObjectMethod(
                env->FindClass("java/lang/Float"),
                env->GetStaticMethodID(env->FindClass("java/lang/Float"), "valueOf",
                                       "(F)Ljava/lang/Float;"),
                value
        );
        return newFloat;
    }

    void DumpOpsCanvas_onDrawRect_proxy(void *canvas, const void *skRect, const void *skPaint) {
        SHADOWHOOK_STACK_SCOPE();
        auto rect = reinterpret_cast<const SkRect *>(skRect);
        auto paint = reinterpret_cast<const SkPaint *>(skPaint);
        SHADOWHOOK_CALL_PREV(DumpOpsCanvas_onDrawRect_proxy, canvas, skRect, skPaint);
    }

    void
    DumpOpsCanvas_onDrawTextBlob_proxy(void *canvas, const void *skTextBlob, SkScalar x, SkScalar y,
                                       const void *skPaint) {
        SHADOWHOOK_STACK_SCOPE();
        auto blob = reinterpret_cast<const SkTextBlob *>(skTextBlob);
        auto paint = reinterpret_cast<const SkPaint *>(skPaint);
        auto runRecord = reinterpret_cast<const SkTextBlob::RunRecord *>(SkAlignPtr(
                (uintptr_t) (blob + 1)));
//        auto next = SkToBool(runRecord->fFlags & runRecord->kLast_Flag) ? nullptr
//                                                                        : runRecord->NextUnchecked(
//                        runRecord);
//        auto text = next->textBuffer();
        SHADOWHOOK_CALL_PREV(DumpOpsCanvas_onDrawTextBlob_proxy, canvas, skTextBlob, x, y, skPaint);
    }

    void
    SkCanvas_onDrawGlyphRunList_proxy(void *canvas, const void *glyphRunList, const void *skPaint) {
        SHADOWHOOK_STACK_SCOPE();
        auto runList = reinterpret_cast<const SkGlyphRunList *>(glyphRunList);
        auto blob = runList->blob();
        auto runRecord = reinterpret_cast<const SkTextBlob::RunRecord *>(SkAlignPtr(
                (uintptr_t) (blob + 1)));
//        auto blob = reinterpret_cast<const SkTextBlob *>(skTextBlob);
//        auto paint = reinterpret_cast<const SkPaint *>(skPaint);
//        auto runRecord = reinterpret_cast<const SkGlyph *>(SkAlignPtr(
//                (uintptr_t) (blob + 2)));
//        auto text = runRecord->textBuffer();
        SHADOWHOOK_CALL_PREV(SkCanvas_onDrawGlyphRunList_proxy, canvas, glyphRunList, skPaint);
    }

    void DisplayListData_draw_proxy(void *displayListData, void *canvas) {
        SHADOWHOOK_STACK_SCOPE();
        auto data = reinterpret_cast<android::uirenderer::DisplayListData *>(displayListData);
        if (data->fBytes.get() != nullptr) {
            auto end = data->fBytes.get() + data->fUsed;
            // TODO use (end / skip) as a fixed jarray size
            for (const uint8_t *ptr = data->fBytes.get(); ptr < end;) {
                auto op = (const Op *) ptr;
                auto type = (const android::uirenderer::DisplayListOpType) op->type;
                auto skip = op->skip;
                ptr += skip;
            }
        }
//        SHADOWHOOK_ALLOW_REENTRANT();
        SHADOWHOOK_CALL_PREV(DisplayListData_draw_proxy, displayListData, canvas);
    }

    extern "C"
    JNIEXPORT void JNICALL
    Java_io_sentry_android_core_replay_RenderNodeTracing_nStartRenderNodeTracing(JNIEnv *env,
                                                                                 jclass clazz) {
        if (DisplayListData_draw_hook != nullptr && DumpOpsCanvas_onDrawTextBlob_hook != nullptr &&
            SkCanvas_onDrawTextBlob_hook != nullptr) {
            return;
        }

        DisplayListData_draw_hook = shadowhook_hook_sym_name(
                "libhwui.so",
                "_ZN7android10uirenderer12skiapipeline13DumpOpsCanvas10onDrawRectERK6SkRectRK7SkPaint",
                reinterpret_cast<void *>(DumpOpsCanvas_onDrawRect_proxy),
                nullptr
        );

        DumpOpsCanvas_onDrawTextBlob_hook = shadowhook_hook_sym_name(
                "libhwui.so",
                "_ZN7android10uirenderer12skiapipeline13DumpOpsCanvas14onDrawTextBlobEPK10SkTextBlobffRK7SkPaint",
                reinterpret_cast<void *>(DumpOpsCanvas_onDrawTextBlob_proxy),
                nullptr
        );

        SkCanvas_onDrawTextBlob_hook = shadowhook_hook_sym_name(
                "libhwui.so",
                "_ZN8SkCanvas18onDrawGlyphRunListERK14SkGlyphRunListRK7SkPaint",
                reinterpret_cast<void *>(SkCanvas_onDrawGlyphRunList_proxy),
                nullptr
        );
    }

    extern "C"
    JNIEXPORT void JNICALL
    Java_io_sentry_android_core_replay_RenderNodeTracing_nStopRenderNodeTracing(JNIEnv *env,
                                                                                jclass clazz) {
        if (DisplayListData_draw_hook != nullptr) {
            shadowhook_unhook(DisplayListData_draw_hook);
        }
        if (DumpOpsCanvas_onDrawTextBlob_hook != nullptr) {
            shadowhook_unhook(DumpOpsCanvas_onDrawTextBlob_hook);
        }
        if (SkCanvas_onDrawTextBlob_hook != nullptr) {
            shadowhook_unhook(SkCanvas_onDrawTextBlob_hook);
        }
    }

    extern "C" JNIEXPORT jobject
    JNICALL
    Java_android_graphics_RenderNodeHelper_nGetDisplayList2(JNIEnv *env,
                                                            jclass clazz,
                                                            jlong render_node) {
        auto node = reinterpret_cast<android::uirenderer::RenderNode *>(render_node);
        auto *displayList = node->getDisplayList().asSkiaDl();
        auto data = &displayList->mDisplayList;
        auto renderProperties = &node->properties();

        jclass arrayListClass = env->FindClass("java/util/ArrayList");
        jmethodID arrayListConstructor = env->GetMethodID(arrayListClass, "<init>", "()V");
        jmethodID arrayListConstructorWithI = env->GetMethodID(arrayListClass, "<init>", "(I)V");
        jobject arrayList = env->NewObject(arrayListClass, arrayListConstructor);

        jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

        auto translationX = renderProperties->getX();
        auto translationY = renderProperties->getY();
        if (translationX != 0 || translationY != 0) {
            jobject x = newFloat(env, translationX);
            jobject y = newFloat(env, translationY);
            jobject args = env->NewObject(arrayListClass, arrayListConstructorWithI, 2);
            env->CallBooleanMethod(args, arrayListAdd, x);
            env->CallBooleanMethod(args, arrayListAdd, y);
            jobject props = getProperties(env, "translate", args);
            env->CallBooleanMethod(arrayList, arrayListAdd, props);
        }

        int clipFlags = renderProperties->getClippingFlags();
        if (clipFlags) {
            android::uirenderer::Rect clipRect;
            renderProperties->getClippingRectForFlags(clipFlags, &clipRect);
            jobject beginPathProps = getProperties(env, "beginPath", nullptr);
            env->CallBooleanMethod(arrayList, arrayListAdd, beginPathProps);

            jobject x = newFloat(env, clipRect.left);
            jobject y = newFloat(env, clipRect.top);
            jobject width = newFloat(env, clipRect.right - clipRect.left);
            jobject height = newFloat(env, clipRect.bottom - clipRect.top);
            jobject rectArgs = env->NewObject(arrayListClass, arrayListConstructorWithI, 4);
            env->CallBooleanMethod(rectArgs, arrayListAdd, x);
            env->CallBooleanMethod(rectArgs, arrayListAdd, y);
            env->CallBooleanMethod(rectArgs, arrayListAdd, width);
            env->CallBooleanMethod(rectArgs, arrayListAdd, height);
            jobject rectProps = getProperties(env, "rect", rectArgs);
            env->CallBooleanMethod(arrayList, arrayListAdd, rectProps);

            jobject clipProps = getProperties(env, "clip", nullptr);
            env->CallBooleanMethod(arrayList, arrayListAdd, clipProps);
        }

        if (data->fBytes.get() != nullptr) {
            auto end = data->fBytes.get() + data->fUsed;
            // TODO use (end / skip) as a fixed jarray size
            for (const uint8_t *ptr = data->fBytes.get(); ptr < end;) {
                auto op = (const Op *) ptr;
                auto type = (const android::uirenderer::DisplayListOpType) op->type;
                auto skip = op->skip;
                switch (type) {
                    case android::uirenderer::DisplayListOpType::Flush:
                        // do nothing
                        break;
                    case android::uirenderer::DisplayListOpType::Translate: {
                        auto translate = (const Translate *) op;
                        jobject x = env->CallStaticObjectMethod(
                                env->FindClass("java/lang/Float"),
                                env->GetStaticMethodID(env->FindClass("java/lang/Float"), "valueOf",
                                                       "(F)Ljava/lang/Float;"),
                                translate->dx
                        );
                        jobject y = env->CallStaticObjectMethod(
                                env->FindClass("java/lang/Float"),
                                env->GetStaticMethodID(env->FindClass("java/lang/Float"), "valueOf",
                                                       "(F)Ljava/lang/Float;"),
                                translate->dy
                        );
                        jobject args = env->NewObject(arrayListClass, arrayListConstructorWithI, 2);
                        env->CallBooleanMethod(args, arrayListAdd, x);
                        env->CallBooleanMethod(args, arrayListAdd, y);
                        jobject props = getProperties(env, "translate", args);
                        env->CallBooleanMethod(arrayList, arrayListAdd, props);
                        break;
                    }
                    case android::uirenderer::DisplayListOpType::Save: {
                        jobject props = getProperties(env, "save", nullptr);
                        env->CallBooleanMethod(arrayList, arrayListAdd, props);
                        break;
                    }
                    case android::uirenderer::DisplayListOpType::Restore: {
                        jobject props = getProperties(env, "restore", nullptr);
                        env->CallBooleanMethod(arrayList, arrayListAdd, props);
                        break;
                    }
                    case android::uirenderer::DisplayListOpType::SaveLayer:
                        break;
                    case android::uirenderer::DisplayListOpType::SaveBehind:
                        break;
                    case android::uirenderer::DisplayListOpType::Concat:
                        break;
                    case android::uirenderer::DisplayListOpType::SetMatrix:
                        break;
                    case android::uirenderer::DisplayListOpType::Scale:
                        break;
                    case android::uirenderer::DisplayListOpType::ClipPath:
                        break;
                    case android::uirenderer::DisplayListOpType::ClipRect: {
                        auto clipRect = (const ClipRect *) op;
                        break;
                    }
                    case android::uirenderer::DisplayListOpType::ClipRRect:
                        break;
                    case android::uirenderer::DisplayListOpType::ClipRegion:
                        break;
                    case android::uirenderer::DisplayListOpType::DrawPaint:
                        break;
                    case android::uirenderer::DisplayListOpType::DrawBehind:
                        break;
                    case android::uirenderer::DisplayListOpType::DrawPath:
                        break;
                    case android::uirenderer::DisplayListOpType::DrawRect: {
                        auto drawRect = (const DrawRect *) op;
                        break;
                    }
                    case android::uirenderer::DisplayListOpType::DrawRegion:
                        break;
                    case android::uirenderer::DisplayListOpType::DrawOval:
                        break;
                    case android::uirenderer::DisplayListOpType::DrawArc:
                        break;
                    case android::uirenderer::DisplayListOpType::DrawRRect:
                        break;
                    case android::uirenderer::DisplayListOpType::DrawDRRect:
                        break;
                    case android::uirenderer::DisplayListOpType::DrawAnnotation:
                        break;
                    case android::uirenderer::DisplayListOpType::DrawDrawable: {
                        break;
                    }
                    case android::uirenderer::DisplayListOpType::DrawPicture:
                        break;
                    case android::uirenderer::DisplayListOpType::DrawImage:
                        break;
                    case android::uirenderer::DisplayListOpType::DrawImageRect:
                        break;
                    case android::uirenderer::DisplayListOpType::DrawImageLattice:
                        break;
                    case android::uirenderer::DisplayListOpType::DrawTextBlob: {
//                        auto drawTextBlob = (const DrawTextBlob *) op;
//                        auto runRecord = reinterpret_cast<const SkTextBlob::RunRecord *>(SkAlignPtr(
//                                (uintptr_t) (drawTextBlob->blob.get() + 1)));
                        break;
                    }
                    case android::uirenderer::DisplayListOpType::DrawPatch:
                        break;
                    case android::uirenderer::DisplayListOpType::DrawPoints:
                        break;
                    case android::uirenderer::DisplayListOpType::DrawVertices:
                        break;
                    case android::uirenderer::DisplayListOpType::DrawAtlas:
                        break;
                    case android::uirenderer::DisplayListOpType::DrawShadowRec:
                        break;
                    case android::uirenderer::DisplayListOpType::DrawVectorDrawable:
                        break;
                    case android::uirenderer::DisplayListOpType::DrawRippleDrawable:
                        break;
                    case android::uirenderer::DisplayListOpType::DrawWebView:
                        break;
                }
                ptr += skip;
            }
        }
        return arrayList;
    }
}
