package io.sentry.android.replay

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.Canvas
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
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt
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
    private val pendingViewHierarchy = AtomicReference<ViewHierarchyNode>()
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
        val viewHierarchy = pendingViewHierarchy.get()

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

                        val screenshot = scaledBitmap.copy(ARGB_8888, false)
                        screenshotRecorderCallback.onScreenshotRecorded(screenshot)
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

        val time = measureTimeMillis {
            val rootNode = ViewHierarchyNode.fromView(root)
            root.traverse(rootNode)
            pendingViewHierarchy.set(rootNode)
        }
        options.logger.log(DEBUG, "Took %d ms to capture view hierarchy", time)

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

public data class ScreenshotRecorderConfig(
    val recordingWidth: Int,
    val recordingHeight: Int,
    val scaleFactor: Float,
    val frameRate: Int,
    val bitRate: Int
) {
    companion object {
        fun from(context: Context, targetHeight: Int, sentryReplayOptions: SentryReplayOptions): ScreenshotRecorderConfig {
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
            val aspectRatio = screenBounds.height().toFloat() / screenBounds.width().toFloat()

            return ScreenshotRecorderConfig(
                recordingWidth = (targetHeight / aspectRatio).roundToInt(),
                recordingHeight = targetHeight,
                scaleFactor = targetHeight.toFloat() / screenBounds.height(),
                frameRate = sentryReplayOptions.frameRate,
                bitRate = sentryReplayOptions.bitRate
            )
        }
    }
}

interface ScreenshotRecorderCallback {
    fun onScreenshotRecorded(bitmap: Bitmap)
}
