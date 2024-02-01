package io.sentry.android.replay

import android.content.Context
import android.content.res.Configuration
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.media.CamcorderProfile
import android.util.DisplayMetrics
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

  companion object {
    private const val TAG = "WindowRecorder"
  }

  private val rootViewsSpy by lazy(NONE) {
    RootViewsSpy.install()
  }

  private var encoder: SimpleVideoEncoder? = null
  private val isRecording = AtomicBoolean(false)
  private val recorders: WeakHashMap<View, ViewTreeObserver.OnDrawListener> = WeakHashMap()

  private val onRootViewsChangedListener = OnRootViewsChangedListener { root, added ->
    if (added) {
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
    val aspectRatio = wm.currentWindowMetrics.bounds.bottom.toFloat() / wm.currentWindowMetrics.bounds.right.toFloat()

    val videoFile = File(context.cacheDir, "sentry-sr.mp4")
    encoder = SimpleVideoEncoder(
      MuxerConfig(
        videoFile,
        videoWidth = (720 / aspectRatio).roundToInt(),
        videoHeight = 720,
        frameRate = 1f,
        bitrate = 500 * 1000,
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
