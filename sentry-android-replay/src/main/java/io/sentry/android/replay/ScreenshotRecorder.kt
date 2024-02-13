package io.sentry.android.replay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.system.measureTimeMillis

internal class ScreenshotRecorder(
  val rootView: WeakReference<View>,
  val encoder: SimpleVideoEncoder
) : ViewTreeObserver.OnDrawListener {

  private val thread = HandlerThread("SentryReplay").also { it.start() }
  private val handler = Handler(thread.looper)
  private val bitmapToVH = WeakHashMap<Bitmap, ViewHierarchyNode>()

  companion object {
    const val TAG = "ScreenshotRecorder"
  }

  private var lastCapturedAtMs: Long? = null
  override fun onDraw() {
    val now = SystemClock.uptimeMillis()
    if (lastCapturedAtMs != null && (now - lastCapturedAtMs!!) < 1000L) {
      return
    }
    lastCapturedAtMs = now

    val root = rootView.get()
    if (root == null || root.width <= 0 || root.height <= 0) {
      return
    }

    val window = root.phoneWindow ?: return
    val bitmap = Bitmap.createBitmap(
      root.width,
      root.height,
      Bitmap.Config.ARGB_8888
    )
    Log.e("BITMAP CREATED", bitmap.toString())

    val time = measureTimeMillis {
      val rootNode = ViewHierarchyNode.fromView(root)
      root.traverse(rootNode)
      bitmapToVH[bitmap] = rootNode
    }
    Log.e("TIME", time.toString())

//    val latch = CountDownLatch(1)

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

          if (viewHierarchy != null) {
            val canvas = Canvas(bitmap)
            viewHierarchy.traverse {
              if (it.shouldRedact && (it.width > 0 && it.height > 0)) {
                it.visibleRect ?: return@traverse

                val paint = Paint().apply {
                  color = it.dominantColor ?: Color.BLACK
                }
                canvas.drawRoundRect(RectF(it.visibleRect), 10f, 10f, paint)
              }
            }
          }

          val scaledBitmap = Bitmap.createScaledBitmap(
            bitmap,
            encoder.muxerConfig.videoWidth,
            encoder.muxerConfig.videoHeight,
            true
          )
//        val baos = ByteArrayOutputStream()
//        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)
//        val bmp = BitmapFactory.decodeByteArray(baos.toByteArray(), 0, baos.size())
          encoder.encode(scaledBitmap)
//        bmp.recycle()
          scaledBitmap.recycle()
          bitmap.recycle()
          Log.i(TAG, "Captured a screenshot")
//          latch.countDown()
        },
        handler
      )
    }

//    val success = latch.await(200, MILLISECONDS)
//    Log.i(TAG, "Captured a screenshot: $success")
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
