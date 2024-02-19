package io.sentry.android.replay

import android.content.Context
import android.graphics.Point
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import io.sentry.android.replay.video.MuxerConfig
import io.sentry.android.replay.video.SimpleVideoEncoder
import java.io.File
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.LazyThreadSafetyMode.NONE
import kotlin.math.roundToInt

class WindowRecorder {

    private val rootViewsSpy by lazy(NONE) {
        RootViewsSpy.install()
    }

    private var encoder: SimpleVideoEncoder? = null
    private val isRecording = AtomicBoolean(false)
    private val recorders: WeakHashMap<View, ViewTreeObserver.OnDrawListener> = WeakHashMap()

    private val onRootViewsChangedListener = OnRootViewsChangedListener { root, added ->
        if (added) {
            if (recorders.containsKey(root)) {
                // TODO: log
                return@OnRootViewsChangedListener
            }
            // stop tracking other windows so they don't interfere in the recording like a 25th frame effect
            recorders.entries.forEach {
                it.key.viewTreeObserver.removeOnDrawListener(it.value)
            }

            val recorder = ScreenshotRecorder(WeakReference(root), encoder!!)
            recorders[root] = recorder
            root.viewTreeObserver?.addOnDrawListener(recorder)
        } else {
            root.viewTreeObserver?.removeOnDrawListener(recorders[root])
            recorders.remove(root)

            recorders.entries.forEach {
                it.key.viewTreeObserver.addOnDrawListener(it.value)
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
        val aspectRatio = if (VERSION.SDK_INT >= VERSION_CODES.R) {
            wm.currentWindowMetrics.bounds.bottom.toFloat() / wm.currentWindowMetrics.bounds.right.toFloat()
        } else {
            val screenResolution = Point()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealSize(screenResolution)
            screenResolution.y.toFloat() / screenResolution.x.toFloat()
        }

        val videoFile = File(context.cacheDir, "sentry-sr.mp4")
        encoder = SimpleVideoEncoder(
            MuxerConfig(
                videoFile,
                videoWidth = (720 / aspectRatio).roundToInt(),
                videoHeight = 720,
                frameRate = 1f,
                bitrate = 500 * 1000
            )
        )
        encoder?.start()
        rootViewsSpy.listeners += onRootViewsChangedListener
    }

    fun stopRecording() {
        rootViewsSpy.listeners -= onRootViewsChangedListener
        recorders.entries.forEach {
            it.key.viewTreeObserver.removeOnDrawListener(it.value)
        }
        recorders.clear()
        encoder?.startRelease()
        encoder = null
    }
}