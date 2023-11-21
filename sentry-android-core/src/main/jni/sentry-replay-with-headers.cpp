#include <jni.h>
#include <RenderNode.h>
#include <SkRRect.h>
#include <SkTextBlob.h>
#include <SkiaDisplayList.h>

namespace {
//    static const SkRect kUnset = {SK_ScalarInfinity, 0, 0, 0};
//    static const SkRect* maybe_unset(const SkRect& r) {
//        return r.left() == SK_ScalarInfinity ? nullptr : &r;
//    }
//
//#define X(T) T,
//    enum class Type : uint8_t {
//#include "DisplayListOps.in"
//    };
//#undef X
//
    struct Op {
        uint32_t type : 8;
        uint32_t skip : 24;
    };
//    static_assert(sizeof(Op) == 4, "");
//
//    struct Flush final : Op {
//        static const auto kType = Type::Flush;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->flush(); }
//    };
//
//    struct Save final : Op {
//        static const auto kType = Type::Save;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->save(); }
//    };
//    struct Restore final : Op {
//        static const auto kType = Type::Restore;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->restore(); }
//    };
//    struct SaveLayer final : Op {
//        static const auto kType = Type::SaveLayer;
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
//        static const auto kType = Type::SaveBehind;
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
//        static const auto kType = Type::Concat;
//        Concat(const SkM44& matrix) : matrix(matrix) {}
//        SkM44 matrix;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->concat(matrix); }
//    };
//    struct SetMatrix final : Op {
//        static const auto kType = Type::SetMatrix;
//        SetMatrix(const SkM44& matrix) : matrix(matrix) {}
//        SkM44 matrix;
//        void draw(SkCanvas* c, const SkMatrix& original) const {
//            c->setMatrix(SkM44(original) * matrix);
//        }
//    };
//    struct Scale final : Op {
//        static const auto kType = Type::Scale;
//        Scale(SkScalar sx, SkScalar sy) : sx(sx), sy(sy) {}
//        SkScalar sx, sy;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->scale(sx, sy); }
//    };
//    struct Translate final : Op {
//        static const auto kType = Type::Translate;
//        Translate(SkScalar dx, SkScalar dy) : dx(dx), dy(dy) {}
//        SkScalar dx, dy;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->translate(dx, dy); }
//    };
//
//    struct ClipPath final : Op {
//        static const auto kType = Type::ClipPath;
//        ClipPath(const SkPath& path, SkClipOp op, bool aa) : path(path), op(op), aa(aa) {}
//        SkPath path;
//        SkClipOp op;
//        bool aa;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->clipPath(path, op, aa); }
//    };
//    struct ClipRect final : Op {
//        static const auto kType = Type::ClipRect;
//        ClipRect(const SkRect& rect, SkClipOp op, bool aa) : rect(rect), op(op), aa(aa) {}
//        SkRect rect;
//        SkClipOp op;
//        bool aa;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->clipRect(rect, op, aa); }
//    };
//    struct ClipRRect final : Op {
//        static const auto kType = Type::ClipRRect;
//        ClipRRect(const SkRRect& rrect, SkClipOp op, bool aa) : rrect(rrect), op(op), aa(aa) {}
//        SkRRect rrect;
//        SkClipOp op;
//        bool aa;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->clipRRect(rrect, op, aa); }
//    };
//    struct ClipRegion final : Op {
//        static const auto kType = Type::ClipRegion;
//        ClipRegion(const SkRegion& region, SkClipOp op) : region(region), op(op) {}
//        SkRegion region;
//        SkClipOp op;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->clipRegion(region, op); }
//    };
//    struct ResetClip final : Op {
//        static const auto kType = Type::ResetClip;
//        ResetClip() {}
//        void draw(SkCanvas* c, const SkMatrix&) const {
////            SkAndroidFrameworkUtils::ResetClip(c);
//        }
//    };
//
//    struct DrawPaint final : Op {
//        static const auto kType = Type::DrawPaint;
//        DrawPaint(const SkPaint& paint) : paint(paint) {}
//        SkPaint paint;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->drawPaint(paint); }
//    };
//    struct DrawBehind final : Op {
//        static const auto kType = Type::DrawBehind;
//        DrawBehind(const SkPaint& paint) : paint(paint) {}
//        SkPaint paint;
//        void draw(SkCanvas* c, const SkMatrix&) const {
////            SkCanvasPriv::DrawBehind(c, paint);
//            }
//    };
//    struct DrawPath final : Op {
//        static const auto kType = Type::DrawPath;
//        DrawPath(const SkPath& path, const SkPaint& paint) : path(path), paint(paint) {}
//        SkPath path;
//        SkPaint paint;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->drawPath(path, paint); }
//    };
//    struct DrawRect final : Op {
//        static const auto kType = Type::DrawRect;
//        DrawRect(const SkRect& rect, const SkPaint& paint) : rect(rect), paint(paint) {}
//        SkRect rect;
//        SkPaint paint;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->drawRect(rect, paint); }
//    };
//    struct DrawRegion final : Op {
//        static const auto kType = Type::DrawRegion;
//        DrawRegion(const SkRegion& region, const SkPaint& paint) : region(region), paint(paint) {}
//        SkRegion region;
//        SkPaint paint;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->drawRegion(region, paint); }
//    };
//    struct DrawOval final : Op {
//        static const auto kType = Type::DrawOval;
//        DrawOval(const SkRect& oval, const SkPaint& paint) : oval(oval), paint(paint) {}
//        SkRect oval;
//        SkPaint paint;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->drawOval(oval, paint); }
//    };
//    struct DrawArc final : Op {
//        static const auto kType = Type::DrawArc;
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
//        static const auto kType = Type::DrawRRect;
//        DrawRRect(const SkRRect& rrect, const SkPaint& paint) : rrect(rrect), paint(paint) {}
//        SkRRect rrect;
//        SkPaint paint;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->drawRRect(rrect, paint); }
//    };
//    struct DrawDRRect final : Op {
//        static const auto kType = Type::DrawDRRect;
//        DrawDRRect(const SkRRect& outer, const SkRRect& inner, const SkPaint& paint)
//                : outer(outer), inner(inner), paint(paint) {}
//        SkRRect outer, inner;
//        SkPaint paint;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->drawDRRect(outer, inner, paint); }
//    };
//    struct DrawDrawable final : Op {
//        static const auto kType = Type::DrawDrawable;
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
//    enum class DrawTextBlobMode {
//        Normal,
//        HctOutline,
//        HctInner,
//    };
//    inline DrawTextBlobMode gDrawTextBlobMode = DrawTextBlobMode::Normal;
//    struct DrawTextBlob final : Op {
//        static const auto kType = Type::DrawTextBlob;
//        DrawTextBlob(const SkTextBlob* blob, SkScalar x, SkScalar y, const SkPaint& paint)
//                : blob(sk_ref_sp(blob)), x(x), y(y), paint(paint), drawTextBlobMode(gDrawTextBlobMode) {}
//        sk_sp<const SkTextBlob> blob;
//        SkScalar x, y;
//        SkPaint paint;
//        DrawTextBlobMode drawTextBlobMode;
//        void draw(SkCanvas* c, const SkMatrix&) const { c->drawTextBlob(blob.get(), x, y, paint); }
//    };
//
    enum ClipEdgeStyle {
        kHard_ClipEdgeStyle,
        kSoft_ClipEdgeStyle
    };

    enum SrcRectConstraint {
        kStrict_SrcRectConstraint, //!< sample only inside bounds; slower
        kFast_SrcRectConstraint,   //!< sample outside bounds; faster
    };

    struct Lattice {

        /** \enum SkCanvas::Lattice::RectType
            Optional setting per rectangular grid entry to make it transparent,
            or to fill the grid entry with a color.
        */
        enum RectType : uint8_t {
            kDefault     = 0, //!< draws SkBitmap into lattice rectangle
            kTransparent,     //!< skips lattice rectangle by making it transparent
            kFixedColor,      //!< draws one of fColors into lattice rectangle
        };

        const int*      fXDivs;     //!< x-axis values dividing bitmap
        const int*      fYDivs;     //!< y-axis values dividing bitmap
        const RectType* fRectTypes; //!< array of fill types
        int             fXCount;    //!< number of x-coordinates
        int             fYCount;    //!< number of y-coordinates
        const SkIRect*  fBounds;    //!< source bounds to draw from
        const SkColor*  fColors;    //!< array of colors
    };

                class VirtualCanvas {
                public:
                    VirtualCanvas(
                            const android::uirenderer::skiapipeline::SkiaDisplayList &displayList)
                            : mDisplayList(displayList) {}
                protected:
                    void onClipRect(const SkRect &rect, SkClipOp, ClipEdgeStyle) {
                    }

                    void onClipRRect(const SkRRect &rrect, SkClipOp, ClipEdgeStyle) {
                    }

                    void onClipPath(const SkPath &path, SkClipOp, ClipEdgeStyle) {
                    }

                    void onClipRegion(const SkRegion &deviceRgn, SkClipOp) {
                    }

                    void onResetClip() {
                    }

                    void onDrawPaint(const SkPaint &) {
                    }

                    void onDrawPath(const SkPath &, const SkPaint &) {
                    }

                    void onDrawRect(const SkRect &, const SkPaint &) {
                    }

                    void onDrawRegion(const SkRegion &, const SkPaint &) {
                    }

                    void onDrawOval(const SkRect &, const SkPaint &) {
                    }

                    void
                    onDrawArc(const SkRect &, SkScalar, SkScalar, bool, const SkPaint &) {
                    }

                    void onDrawRRect(const SkRRect &, const SkPaint &) {
                    }

                    void onDrawDRRect(const SkRRect &, const SkRRect &, const SkPaint &) {
                    }

                    void onDrawTextBlob(const SkTextBlob *, SkScalar, SkScalar,
                                        const SkPaint &) {
                    }

                    void onDrawImage2(const SkImage *, SkScalar dx, SkScalar dy,
                                      const SkSamplingOptions &,
                                      const SkPaint *) {
                    }

                    void onDrawImageRect2(const SkImage *, const SkRect &, const SkRect &,
                                          const SkSamplingOptions &,
                                          const SkPaint *, SrcRectConstraint) {
                    }

                    void
                    onDrawImageLattice2(const SkImage *, const Lattice &lattice, const SkRect &dst,
                                        SkFilterMode, const SkPaint *) {
                    }

                    void onDrawPoints(SkCanvas::PointMode, size_t, const SkPoint[],
                                      const SkPaint &) {
                    }

                    void
                    onDrawPicture(const SkPicture *, const SkMatrix *, const SkPaint *) {
                    }

                    void onDrawDrawable(SkDrawable *drawable, const SkMatrix *) {
                        auto renderNodeDrawable = getRenderNodeDrawable(drawable);
                        if (nullptr != renderNodeDrawable) {
                            return;
                        }
                        auto glFunctorDrawable = getFunctorDrawable(drawable);
                        if (nullptr != glFunctorDrawable) {
                            return;
                        }
                    }
                private:
                    const android::uirenderer::skiapipeline::RenderNodeDrawable *
                    getRenderNodeDrawable(SkDrawable *drawable) {
                        for (auto &child: mDisplayList.mChildNodes) {
                            if (drawable == &child) {
                                return &child;
                            }
                        }
                        return nullptr;
                    }

                    android::uirenderer::skiapipeline::FunctorDrawable *
                    getFunctorDrawable(SkDrawable *drawable) {
                        for (auto &child: mDisplayList.mChildFunctors) {
                            if (drawable == reinterpret_cast<SkDrawable *>(child)) {
                                return child;
                            }
                        }
                        return nullptr;
                    }

                    int mLevel;
                    const android::uirenderer::skiapipeline::SkiaDisplayList &mDisplayList;
                    std::string mIdent;
                };

    extern "C" JNIEXPORT void
    JNICALL
    Java_android_graphics_RenderNodeHelper_nGetDisplayList2(JNIEnv *env,
                                                            jclass clazz,
                                                            jlong render_node) {
        auto node = reinterpret_cast<android::uirenderer::RenderNode *>(render_node);
        auto* displayList = node->getDisplayList().asSkiaDl();
        auto data = &displayList->mDisplayList;

//        VirtualCanvas canvas(*displayList);
//        AutoCanvasRestore acr(&canvas, false);

        if (data->fBytes.get() != nullptr) {
            auto end = data->fBytes.get() + data->fUsed;
            for (const uint8_t* ptr = data->fBytes.get(); ptr < end;) {
                auto op = (const Op*)ptr;
                auto type = op->type;
                auto skip = op->skip;
                ptr += skip;
            }
        }
//        VirtualCanvas canvas(*displayList);
//        displayList->draw(reinterpret_cast<SkCanvas *>(&canvas));
    }
}
