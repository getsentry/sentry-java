package io.sentry.android.replay

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import io.sentry.android.replay.video.SimpleVideoEncoder
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.system.measureTimeMillis

// TODO: use ILogger of Sentry and change level
@TargetApi(26)
internal class ScreenshotRecorder(
    val encoder: SimpleVideoEncoder
) : ViewTreeObserver.OnDrawListener {

    private var rootView: WeakReference<View>? = null
    private val thread = HandlerThread("SentryReplay").also { it.start() }
    private val handler = Handler(thread.looper)
    private val bitmapToVH = WeakHashMap<Bitmap, ViewHierarchyNode>()
    private val maskingPaint = Paint()
    private val singlePixelBitmap: Bitmap = Bitmap.createBitmap(
        1,
        1,
        Bitmap.Config.ARGB_8888
    )
    private val singlePixelBitmapCanvas: Canvas = Canvas(singlePixelBitmap)
    private val prescaledMatrix = Matrix().apply {
        preScale(encoder.muxerConfig.scaleFactor, encoder.muxerConfig.scaleFactor)
    }

    companion object {
        const val TAG = "ScreenshotRecorder"
    }

    private var lastCapturedAtMs: Long? = null
    override fun onDraw() {
        // TODO: replace with Debouncer from sentry-core
        val now = SystemClock.uptimeMillis()
        if (lastCapturedAtMs != null && (now - lastCapturedAtMs!!) < 500L) {
            return
        }
        lastCapturedAtMs = now

        val root = rootView?.get()
        if (root == null || root.width <= 0 || root.height <= 0 || !root.isShown) {
            return
        }

        val window = root.phoneWindow ?: return
        val bitmap = Bitmap.createBitmap(
            root.width,
            root.height,
            Bitmap.Config.ARGB_8888
        )

        val time = measureTimeMillis {
            val rootNode = ViewHierarchyNode.fromView(root)
            root.traverse(rootNode)
            bitmapToVH[bitmap] = rootNode
        }
        Log.e("TIME", time.toString())

        // postAtFrontOfQueue to ensure the view hierarchy and bitmap are ase close in-sync as possible
        Handler(Looper.getMainLooper()).postAtFrontOfQueue {
            PixelCopy.request(
                window,
                bitmap,
                { copyResult: Int ->
                    Log.d(TAG, "PixelCopy result: $copyResult")
                    if (copyResult != PixelCopy.SUCCESS) {
                        Log.e(TAG, "Failed to capture screenshot")
                        return@request
                    }

                    Log.e("BITMAP CAPTURED", bitmap.toString())
                    val viewHierarchy = bitmapToVH[bitmap]

                    val scaledBitmap = Bitmap.createScaledBitmap(
                        bitmap,
                        encoder.muxerConfig.videoWidth,
                        encoder.muxerConfig.videoHeight,
                        true
                    )

                    if (viewHierarchy == null) {
                        Log.e(TAG, "Failed to determine view hierarchy, not capturing")
                        return@request
                    } else {
                        val canvas = Canvas(scaledBitmap)
                        canvas.setMatrix(prescaledMatrix)
                        viewHierarchy.traverse {
                            if (it.shouldRedact && (it.width > 0 && it.height > 0)) {
                                it.visibleRect ?: return@traverse

                                // TODO: check for view type rather than rely on absence of dominantColor here
                                val color = if (it.dominantColor == null) {
                                    singlePixelBitmapCanvas.drawBitmap(bitmap, it.visibleRect, Rect(0, 0, 1, 1), null)
                                    singlePixelBitmap.getPixel(0, 0)
                                } else {
                                    it.dominantColor
                                }

                                maskingPaint.setColor(color)
                                canvas.drawRoundRect(RectF(it.visibleRect), 10f, 10f, maskingPaint)
                            }
                        }
                    }

//        val baos = ByteArrayOutputStream()
//        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)
//        val bmp = BitmapFactory.decodeByteArray(baos.toByteArray(), 0, baos.size())
                    encoder.encode(scaledBitmap)
//        bmp.recycle()
                    scaledBitmap.recycle()
                    bitmap.recycle()
                    Log.i(TAG, "Captured a screenshot")
                },
                handler
            )
        }
    }

    fun bind(root: View) {
        // first unbind the current root
        unbind(rootView?.get())
        rootView?.clear()

        // next bind the new root
        rootView = WeakReference(root)
        root.viewTreeObserver?.addOnDrawListener(this)
    }

    fun unbind(root: View?) {
        root?.viewTreeObserver?.removeOnDrawListener(this)
    }

    fun close() {
        unbind(rootView?.get())
        rootView?.clear()
        thread.quitSafely()
    }

    private fun ViewHierarchyNode.traverse(callback: (ViewHierarchyNode) -> Unit) {
        callback(this)
        if (this.children != null) {
            this.children!!.forEach {
                it.traverse(callback)
            }
        }
    }

    private fun View.traverse(parentNode: ViewHierarchyNode) {
        if (this !is ViewGroup) {
            return
        }

        if (this.childCount == 0) {
            return
        }

        val childNodes = ArrayList<ViewHierarchyNode>(this.childCount)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child != null) {
                val childNode = ViewHierarchyNode.fromView(child)
                childNodes.add(childNode)
                child.traverse(childNode)
            }
        }
        parentNode.children = childNodes
    }
}
