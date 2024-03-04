package io.sentry.android.replay

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Point
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.view.View
import android.view.WindowManager
import io.sentry.android.replay.video.MuxerConfig
import io.sentry.android.replay.video.SimpleVideoEncoder
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.LazyThreadSafetyMode.NONE
import kotlin.math.roundToInt

@TargetApi(26)
class WindowRecorder {

    private val rootViewsSpy by lazy(NONE) {
        RootViewsSpy.install()
    }

    private var encoder: SimpleVideoEncoder? = null
    private val isRecording = AtomicBoolean(false)
    private val rootViews = ArrayList<WeakReference<View>>()
    private var recorder: ScreenshotRecorder? = null

    private val onRootViewsChangedListener = OnRootViewsChangedListener { root, added ->
        if (added) {
            rootViews.add(WeakReference(root))
            recorder?.bind(root)
        } else {
            recorder?.unbind(root)
            rootViews.removeAll { it.get() == root }

            val newRoot = rootViews.lastOrNull()?.get()
            if (newRoot != null && root != newRoot) {
                recorder?.bind(newRoot)
            }
        }
    }

    fun startRecording(context: Context) {
        if (isRecording.getAndSet(true)) {
            return
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
//    val (height, width) = (wm.currentWindowMetrics.bounds.bottom /
//        context.resources.displayMetrics.density).roundToInt() to
//        (wm.currentWindowMetrics.bounds.right /
//            context.resources.displayMetrics.density).roundToInt()
        // TODO: API level check
        // PixelCopy takes screenshots including system bars, so we have to get the real size here
        val height: Int
        val aspectRatio = if (VERSION.SDK_INT >= VERSION_CODES.R) {
            height = wm.currentWindowMetrics.bounds.bottom
            height.toFloat() / wm.currentWindowMetrics.bounds.right.toFloat()
        } else {
            val screenResolution = Point()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealSize(screenResolution)
            height = screenResolution.y
            height.toFloat() / screenResolution.x.toFloat()
        }

        val videoFile = File(context.cacheDir, "sentry-sr.mp4")
        encoder = SimpleVideoEncoder(
            MuxerConfig(
                videoFile,
                videoWidth = (720 / aspectRatio).roundToInt(),
                videoHeight = 720,
                scaleFactor = 720f / height,
                frameRate = 2f,
                bitrate = 500 * 1000
            )
        ).also { it.start() }
        recorder = ScreenshotRecorder(encoder!!)
        rootViewsSpy.listeners += onRootViewsChangedListener
    }

    fun stopRecording() {
        rootViewsSpy.listeners -= onRootViewsChangedListener
        rootViews.forEach { recorder?.unbind(it.get()) }
        recorder?.close()
        rootViews.clear()
        recorder = null
        encoder?.startRelease()
        encoder = null
        isRecording.set(false)
    }
}
