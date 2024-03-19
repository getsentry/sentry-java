package io.sentry.android.replay

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.INFO
import io.sentry.SentryOptions
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.measureTimeMillis

@TargetApi(26)
internal class ScreenshotRecorder(
    val config: ScreenshotRecorderConfig,
    val options: SentryOptions,
    private val screenshotRecorderCallback: ScreenshotRecorderCallback
) : ViewTreeObserver.OnDrawListener {

    private var rootView: WeakReference<View>? = null
    private val thread = HandlerThread("SentryReplayRecorder").also { it.start() }
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
        preScale(config.scaleFactor, config.scaleFactor)
    }
    private val contentChanged = AtomicBoolean(false)
    private val isCapturing = AtomicBoolean(true)
    private var lastScreenshot: Bitmap? = null

    fun capture() {
        if (!isCapturing.get()) {
            options.logger.log(DEBUG, "ScreenshotRecorder is paused, not capturing screenshot")
            return
        }

        if (!contentChanged.get() && lastScreenshot != null) {
            options.logger.log(DEBUG, "Content hasn't changed, repeating last known frame")

            lastScreenshot?.let {
                screenshotRecorderCallback.onScreenshotRecorded(
                    it.copy(ARGB_8888, false)
                )
            }
            return
        }

        val root = rootView?.get()
        if (root == null || root.width <= 0 || root.height <= 0 || !root.isShown) {
            options.logger.log(DEBUG, "Root view is invalid, not capturing screenshot")
            return
        }

        val window = root.phoneWindow ?: return
        val bitmap = Bitmap.createBitmap(
            root.width,
            root.height,
            Bitmap.Config.ARGB_8888
        )

        // postAtFrontOfQueue to ensure the view hierarchy and bitmap are ase close in-sync as possible
        Handler(Looper.getMainLooper()).postAtFrontOfQueue {
            val time = measureTimeMillis {
                val rootNode = ViewHierarchyNode.fromView(root)
                root.traverse(rootNode)
                bitmapToVH[bitmap] = rootNode
            }
            options.logger.log(DEBUG, "Took %d ms to capture view hierarchy", time)

            PixelCopy.request(
                window,
                bitmap,
                { copyResult: Int ->
                    if (copyResult != PixelCopy.SUCCESS) {
                        options.logger.log(INFO, "Failed to capture replay recording: %d", copyResult)
                        return@request
                    }

                    val viewHierarchy = bitmapToVH[bitmap]
                    val scaledBitmap: Bitmap

                    if (viewHierarchy == null) {
                        options.logger.log(INFO, "Failed to determine view hierarchy, not capturing")
                        return@request
                    } else {
                        scaledBitmap = Bitmap.createScaledBitmap(
                            bitmap,
                            config.recordingWidth,
                            config.recordingHeight,
                            true
                        )
                        val canvas = Canvas(scaledBitmap)
                        canvas.setMatrix(prescaledMatrix)
                        viewHierarchy.traverse {
//                            if (it.shouldRedact && (it.width > 0 && it.height > 0)) {
//                                it.visibleRect ?: return@traverse
//
//                                // TODO: check for view type rather than rely on absence of dominantColor here
//                                val color = if (it.dominantColor == null) {
//                                    singlePixelBitmapCanvas.drawBitmap(bitmap, it.visibleRect, Rect(0, 0, 1, 1), null)
//                                    singlePixelBitmap.getPixel(0, 0)
//                                } else {
//                                    it.dominantColor
//                                }
//
//                                maskingPaint.setColor(color)
//                                canvas.drawRoundRect(RectF(it.visibleRect), 10f, 10f, maskingPaint)
//                            }
                        }
                    }

                    val screenshot = scaledBitmap.copy(ARGB_8888, false)
                    screenshotRecorderCallback.onScreenshotRecorded(screenshot)
                    lastScreenshot = screenshot
                    contentChanged.set(false)

                    scaledBitmap.recycle()
                    bitmap.recycle()
                    bitmapToVH.remove(bitmap)
                },
                handler
            )
        }
    }

    override fun onDraw() {
        contentChanged.set(true)
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

    fun pause() {
        isCapturing.set(false)
        unbind(rootView?.get())
    }

    fun resume() {
        // can't use bind() as it will invalidate the weakref
        rootView?.get()?.viewTreeObserver?.addOnDrawListener(this)
        isCapturing.set(true)
    }

    fun close() {
        unbind(rootView?.get())
        rootView?.clear()
        lastScreenshot?.recycle()
        bitmapToVH.clear()
        isCapturing.set(false)
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

internal data class ScreenshotRecorderConfig(
    val recordingWidth: Int,
    val recordingHeight: Int,
    val scaleFactor: Float,
    val frameRate: Int = 2
)

interface ScreenshotRecorderCallback {
    fun onScreenshotRecorded(bitmap: Bitmap)
}
