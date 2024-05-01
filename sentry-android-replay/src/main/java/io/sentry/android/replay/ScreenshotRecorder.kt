package io.sentry.android.replay

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import io.sentry.SentryLevel.DEBUG
import io.sentry.SentryLevel.INFO
import io.sentry.SentryLevel.WARNING
import io.sentry.SentryOptions
import io.sentry.SentryReplayOptions
import io.sentry.android.replay.util.getVisibleRects
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.ImageViewHierarchyNode
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.TextViewHierarchyNode
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@TargetApi(26)
internal class ScreenshotRecorder(
    val config: ScreenshotRecorderConfig,
    val options: SentryOptions,
    private val screenshotRecorderCallback: ScreenshotRecorderCallback?
) : ViewTreeObserver.OnDrawListener {

    private var rootView: WeakReference<View>? = null
    private val thread = HandlerThread("SentryReplayRecorder").also { it.start() }
    private val handler = Handler(thread.looper)
    private val pendingViewHierarchy = AtomicReference<ViewHierarchyNode>()
    private val maskingPaint = Paint()
    private val singlePixelBitmap: Bitmap = Bitmap.createBitmap(
        1,
        1,
        Bitmap.Config.ARGB_8888
    )
    private val singlePixelBitmapCanvas: Canvas = Canvas(singlePixelBitmap)
    private val prescaledMatrix = Matrix().apply {
        preScale(config.scaleFactorX, config.scaleFactorY)
    }
    private val contentChanged = AtomicBoolean(false)
    private val isCapturing = AtomicBoolean(true)
    private var lastScreenshot: Bitmap? = null

    @OptIn(ExperimentalTime::class)
    fun capture() {
        val viewHierarchy = pendingViewHierarchy.getAndSet(null)

        if (!isCapturing.get()) {
            options.logger.log(DEBUG, "ScreenshotRecorder is paused, not capturing screenshot")
            return
        }

        if (!contentChanged.get() && lastScreenshot != null) {
            options.logger.log(DEBUG, "Content hasn't changed, repeating last known frame")

            lastScreenshot?.let {
                screenshotRecorderCallback?.onScreenshotRecorded(
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

        val window = root.phoneWindow
        if (window == null) {
            options.logger.log(DEBUG, "Window is invalid, not capturing screenshot")
            return
        }

        val bitmap = Bitmap.createBitmap(
            root.width,
            root.height,
            Bitmap.Config.ARGB_8888
        )

        // postAtFrontOfQueue to ensure the view hierarchy and bitmap are ase close in-sync as possible
        Handler(Looper.getMainLooper()).postAtFrontOfQueue {
//            val viewHierarchy: ViewHierarchyNode
//            val time = measureTime {
//                val rootNode = ViewHierarchyNode.fromView(root, options)
//                root.traverse(rootNode)
//                viewHierarchy = rootNode
//            }
//            Log.e("Recorder", "Time to get view hierarchy: $time")

            try {
                PixelCopy.request(
                    window,
                    bitmap,
                    { copyResult: Int ->
                        if (copyResult != PixelCopy.SUCCESS) {
                            options.logger.log(INFO, "Failed to capture replay recording: %d", copyResult)
                            bitmap.recycle()
                            return@request
                        }

                        val scaledBitmap: Bitmap

                        if (viewHierarchy == null) {
                            options.logger.log(INFO, "Failed to determine view hierarchy, not capturing")
                            bitmap.recycle()
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
                            viewHierarchy.traverse { node ->
                                if (node.shouldRedact && (node.width > 0 && node.height > 0)) {
                                    node.visibleRect ?: return@traverse false

                                    var isObscured = false
                                    viewHierarchy.traverse innerTraverse@{ otherNode ->
                                        otherNode.visibleRect ?: return@innerTraverse false

                                        if (!otherNode.visibleRect.contains(node.visibleRect)) {
                                            return@innerTraverse false
                                        }

                                        if (otherNode.elevation > node.elevation) {
                                            isObscured = true
                                            return@innerTraverse false
                                        }
                                        return@innerTraverse true
                                    }

                                    if (isObscured) {
                                        return@traverse true
                                    }
                                    // TODO: iterate the rest of the tree and check if the view is
                                    // TODO: obscured by any of those, isVisibleToUser does not
                                    // TODO: consider elevation. Basically search for views with
                                    // TODO: higher elevation and check if their visibleRect contains
                                    // TODO: the one of the current view
                                    val (visibleRects, color) = when (node) {
                                        is ImageViewHierarchyNode -> {
                                            singlePixelBitmapCanvas.drawBitmap(
                                                bitmap,
                                                node.visibleRect,
                                                Rect(0, 0, 1, 1),
                                                null
                                            )
                                            listOf(node.visibleRect) to singlePixelBitmap.getPixel(0, 0)
                                        }
                                        is TextViewHierarchyNode -> {
                                            node.layout.getVisibleRects(
                                                node.visibleRect,
                                                node.paddingLeft,
                                                node.paddingTop
                                            ) to (node.dominantColor ?: Color.BLACK)
                                        }
                                        else -> {
                                            listOf(node.visibleRect) to Color.BLACK
                                        }
                                    }

                                    maskingPaint.setColor(color)
                                    visibleRects.forEach { rect ->
                                        canvas.drawRoundRect(RectF(rect), 10f, 10f, maskingPaint)
                                    }
                                }
                                return@traverse true
                            }
                        }

                        val screenshot = scaledBitmap.copy(ARGB_8888, false)
                        screenshotRecorderCallback?.onScreenshotRecorded(screenshot)
                        lastScreenshot?.recycle()
                        lastScreenshot = screenshot
                        contentChanged.set(false)

                        scaledBitmap.recycle()
                        bitmap.recycle()
                    },
                    handler
                )
            } catch (e: Throwable) {
                options.logger.log(WARNING, "Failed to capture replay recording", e)
                bitmap.recycle()
            }
        }
    }

    override fun onDraw() {
        val root = rootView?.get()
        if (root == null || root.width <= 0 || root.height <= 0 || !root.isShown) {
            options.logger.log(DEBUG, "Root view is invalid, not capturing screenshot")
            return
        }

        val rootNode = ViewHierarchyNode.fromView(root, options)
        root.traverse(rootNode)
        pendingViewHierarchy.set(rootNode)

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
        pendingViewHierarchy.set(null)
        isCapturing.set(false)
        thread.quitSafely()
    }

    private fun ViewHierarchyNode.traverse(callback: (ViewHierarchyNode) -> Boolean) {
        val traverseChildren = callback(this)
        if (traverseChildren) {
            if (this.children != null) {
                this.children!!.forEach {
                    it.traverse(callback)
                }
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
                val childNode = ViewHierarchyNode.fromView(child, options)
                childNodes.add(childNode)
                child.traverse(childNode)
            }
        }
        parentNode.children = childNodes
    }
}

public data class ScreenshotRecorderConfig(
    val recordingWidth: Int,
    val recordingHeight: Int,
    val scaleFactorX: Float,
    val scaleFactorY: Float,
    val frameRate: Int,
    val bitRate: Int
) {
    companion object {
        /**
         * Since codec block size is 16, so we have to adjust the width and height to it, otherwise
         * the codec might fail to configure on some devices, see https://cs.android.com/android/platform/superproject/+/master:frameworks/base/media/java/android/media/MediaCodecInfo.java;l=1999-2001
         */
        private fun Int.adjustToBlockSize(): Int {
            val remainder = this % 16
            return if (remainder <= 8) {
                this - remainder
            } else {
                this + (16 - remainder)
            }
        }

        fun from(
            context: Context,
            sessionReplay: SentryReplayOptions
        ): ScreenshotRecorderConfig {
            // PixelCopy takes screenshots including system bars, so we have to get the real size here
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val screenBounds = if (VERSION.SDK_INT >= VERSION_CODES.R) {
                wm.currentWindowMetrics.bounds
            } else {
                val screenBounds = Point()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealSize(screenBounds)
                Rect(0, 0, screenBounds.x, screenBounds.y)
            }

            // use the baseline density of 1x (mdpi)
            val (height, width) =
                (screenBounds.height() / context.resources.displayMetrics.density)
                    .roundToInt()
                    .adjustToBlockSize() to
                    (screenBounds.width() / context.resources.displayMetrics.density)
                        .roundToInt()
                        .adjustToBlockSize()

            return ScreenshotRecorderConfig(
                recordingWidth = width,
                recordingHeight = height,
                scaleFactorX = width.toFloat() / screenBounds.width(),
                scaleFactorY = height.toFloat() / screenBounds.height(),
                frameRate = sessionReplay.frameRate,
                bitRate = sessionReplay.bitRate
            )
        }
    }
}

/**
 * A callback to be invoked when a new screenshot available. Normally, only one of the
 * [onScreenshotRecorded] method overloads should be called by a single recorder, however, it will
 * still work of both are used at the same time.
 */
public interface ScreenshotRecorderCallback {
    /**
     * Called whenever a new frame screenshot is available.
     *
     * @param bitmap a screenshot taken in the form of [android.graphics.Bitmap]
     */
    fun onScreenshotRecorded(bitmap: Bitmap)

    /**
     * Called whenever a new frame screenshot is available.
     *
     * @param screenshot file containing the frame screenshot
     * @param frameTimestamp the timestamp when the frame screenshot was taken
     */
    fun onScreenshotRecorded(screenshot: File, frameTimestamp: Long)
}
