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
import io.sentry.android.replay.util.MainLooperHandler
import io.sentry.android.replay.util.getVisibleRects
import io.sentry.android.replay.util.gracefullyShutdown
import io.sentry.android.replay.util.submitSafely
import io.sentry.android.replay.util.traverse
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.ImageViewHierarchyNode
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.TextViewHierarchyNode
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.LazyThreadSafetyMode.NONE
import kotlin.math.roundToInt

@TargetApi(26)
internal class ScreenshotRecorder(
    val config: ScreenshotRecorderConfig,
    val options: SentryOptions,
    val mainLooperHandler: MainLooperHandler,
    private val screenshotRecorderCallback: ScreenshotRecorderCallback?
) : ViewTreeObserver.OnDrawListener {

    private val recorder by lazy {
        Executors.newSingleThreadScheduledExecutor(RecorderExecutorServiceThreadFactory())
    }
    private var rootView: WeakReference<View>? = null
    private val maskingPaint by lazy(NONE) { Paint() }
    private val singlePixelBitmap: Bitmap by lazy(NONE) {
        Bitmap.createBitmap(
            1,
            1,
            Bitmap.Config.ARGB_8888
        )
    }
    private val singlePixelBitmapCanvas: Canvas by lazy(NONE) { Canvas(singlePixelBitmap) }
    private val prescaledMatrix by lazy(NONE) {
        Matrix().apply {
            preScale(config.scaleFactorX, config.scaleFactorY)
        }
    }
    private val contentChanged = AtomicBoolean(false)
    private val isCapturing = AtomicBoolean(true)
    private var lastScreenshot: Bitmap? = null

    fun capture() {
        if (!isCapturing.get()) {
            options.logger.log(DEBUG, "ScreenshotRecorder is paused, not capturing screenshot")
            return
        }

        if (!contentChanged.get() && lastScreenshot != null && !lastScreenshot!!.isRecycled) {
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
            config.recordingWidth,
            config.recordingHeight,
            Bitmap.Config.ARGB_8888
        )

        // postAtFrontOfQueue to ensure the view hierarchy and bitmap are ase close in-sync as possible
        mainLooperHandler.post {
            try {
                contentChanged.set(false)
                PixelCopy.request(
                    window,
                    bitmap,
                    { copyResult: Int ->
                        if (copyResult != PixelCopy.SUCCESS) {
                            options.logger.log(INFO, "Failed to capture replay recording: %d", copyResult)
                            bitmap.recycle()
                            return@request
                        }

                        // TODO: handle animations with heuristics (e.g. if we fall under this condition 2 times in a row, we should capture)
                        if (contentChanged.get()) {
                            options.logger.log(INFO, "Failed to determine view hierarchy, not capturing")
                            bitmap.recycle()
                            return@request
                        }

                        val viewHierarchy = ViewHierarchyNode.fromView(root, null, 0, options)
                        root.traverse(viewHierarchy, options)

                        recorder.submitSafely(options, "screenshot_recorder.mask") {
                            val canvas = Canvas(bitmap)
                            canvas.setMatrix(prescaledMatrix)
                            viewHierarchy.traverse { node ->
                                if (node.shouldMask && (node.width > 0 && node.height > 0)) {
                                    node.visibleRect ?: return@traverse false

                                    // TODO: investigate why it returns true on RN when it shouldn't
//                                    if (viewHierarchy.isObscured(node)) {
//                                        return@traverse true
//                                    }

                                    val (visibleRects, color) = when (node) {
                                        is ImageViewHierarchyNode -> {
                                            listOf(node.visibleRect) to
                                                bitmap.dominantColorForRect(node.visibleRect)
                                        }

                                        is TextViewHierarchyNode -> {
                                            val textColor = node.layout?.dominantTextColor
                                                ?: node.dominantColor
                                                ?: Color.BLACK
                                            node.layout.getVisibleRects(
                                                node.visibleRect,
                                                node.paddingLeft,
                                                node.paddingTop
                                            ) to textColor
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

                            val screenshot = bitmap.copy(ARGB_8888, false)
                            screenshotRecorderCallback?.onScreenshotRecorded(screenshot)
                            lastScreenshot?.recycle()
                            lastScreenshot = screenshot
                            contentChanged.set(false)

                            bitmap.recycle()
                        }
                    },
                    mainLooperHandler.handler
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

        contentChanged.set(true)
    }

    fun bind(root: View) {
        // first unbind the current root
        unbind(rootView?.get())
        rootView?.clear()

        // next bind the new root
        rootView = WeakReference(root)
        root.viewTreeObserver?.addOnDrawListener(this)
        // invalidate the flag to capture the first frame after new window is attached
        contentChanged.set(true)
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
        isCapturing.set(false)
        recorder.gracefullyShutdown(options)
    }

    private fun Bitmap.dominantColorForRect(rect: Rect): Int {
        // TODO: maybe this ceremony can be just simplified to
        // TODO: multiplying the visibleRect by the prescaledMatrix
        val visibleRect = Rect(rect)
        val visibleRectF = RectF(visibleRect)

        // since we take screenshot with lower scale, we also
        // have to apply the same scale to the visibleRect to get the
        // correct screenshot part to determine the dominant color
        prescaledMatrix.mapRect(visibleRectF)
        // round it back to integer values, because drawBitmap below accepts Rect only
        visibleRectF.round(visibleRect)
        // draw part of the screenshot (visibleRect) to a single pixel bitmap
        singlePixelBitmapCanvas.drawBitmap(
            this,
            visibleRect,
            Rect(0, 0, 1, 1),
            null
        )
        // get the pixel color (= dominant color)
        return singlePixelBitmap.getPixel(0, 0)
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
                val childNode =
                    ViewHierarchyNode.fromView(child, parentNode, indexOfChild(child), options)
                childNodes.add(childNode)
                child.traverse(childNode)
            }
        }
        parentNode.children = childNodes
    }

    private class RecorderExecutorServiceThreadFactory : ThreadFactory {
        private var cnt = 0
        override fun newThread(r: Runnable): Thread {
            val ret = Thread(r, "SentryReplayRecorder-" + cnt++)
            ret.setDaemon(true)
            return ret
        }
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
    internal constructor(
        scaleFactorX: Float,
        scaleFactorY: Float
    ) : this(
        recordingWidth = 0,
        recordingHeight = 0,
        scaleFactorX = scaleFactorX,
        scaleFactorY = scaleFactorY,
        frameRate = 0,
        bitRate = 0
    )

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
                ((screenBounds.height() / context.resources.displayMetrics.density) * sessionReplay.quality.sizeScale)
                    .roundToInt()
                    .adjustToBlockSize() to
                    ((screenBounds.width() / context.resources.displayMetrics.density) * sessionReplay.quality.sizeScale)
                        .roundToInt()
                        .adjustToBlockSize()

            return ScreenshotRecorderConfig(
                recordingWidth = width,
                recordingHeight = height,
                scaleFactorX = width.toFloat() / screenBounds.width(),
                scaleFactorY = height.toFloat() / screenBounds.height(),
                frameRate = sessionReplay.frameRate,
                bitRate = sessionReplay.quality.bitRate
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
