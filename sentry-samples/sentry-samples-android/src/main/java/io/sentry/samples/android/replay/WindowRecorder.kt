package io.sentry.samples.android.replay

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.CanvasDelegate
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import io.sentry.samples.android.replay.RRWebRecorder
import java.util.LinkedList

class WindowRecorder : Window.OnFrameMetricsAvailableListener {

    companion object {
        private const val TAG = "WindowRecorder"
        private const val MIN_TIME_BETWEEN_FRAMES_MS = 1000
    }

    val recorder = RRWebRecorder()
    private var canvasDelegate: CanvasDelegate? = null
    private var canvas: Canvas? = null
    private var activity: Activity? = null
    private var lastCapturedAtMs: Long? = null
    private var startTimeMs: Long = 0

    fun startRecording(activity: Activity) {
        this.activity = activity
        this.startTimeMs = SystemClock.uptimeMillis()

        activity.window.addOnFrameMetricsAvailableListener(this, Handler(Looper.getMainLooper()))
        activity.window.callback = object : WindowCallbackDelegate(activity.window.callback) {

            override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
                event?.let {
                    val timestamp = SystemClock.uptimeMillis() - startTimeMs
                    recorder.onTouchEvent(timestamp, event)
                }
                return super.dispatchTouchEvent(event)
            }
        }
    }

    fun stopRecording() {
        activity?.window?.removeOnFrameMetricsAvailableListener(this)
        activity = null
    }

    override fun onFrameMetricsAvailable(
        window: Window?,
        frameMetrics: FrameMetrics?,
        dropCountSinceLastInvocation: Int
    ) {
        val view = activity?.findViewById<View>(android.R.id.content)
        view?.let {
            captureFrame(it)
        }
    }


    private fun captureFrame(view: View) {
        if (view.width == 0 || view.height == 0 || view.visibility == View.GONE) {
            return
        }

        // cheap debounce for testing
        // TODO remove
        val now = SystemClock.uptimeMillis()
        if (lastCapturedAtMs != null && (now - lastCapturedAtMs!!) < MIN_TIME_BETWEEN_FRAMES_MS) {
            return
        }
        lastCapturedAtMs = now

        if (canvasDelegate == null) {
            val displayMetrics = DisplayMetrics()
            activity!!.windowManager.defaultDisplay.getMetrics(displayMetrics)
            val bitmap = Bitmap.createBitmap(
                displayMetrics.widthPixels,
                displayMetrics.heightPixels,
                Bitmap.Config.ARGB_8888
            )
            canvas = Canvas(bitmap)
            canvasDelegate = CanvasDelegate(
                recorder,
                canvas!!
            )
        }

        // reset the canvas first, as it will be re-used for clipping operations
        canvas!!.restoreToCount(1)
        recorder.beginFrame(now - startTimeMs, view.width, view.height)

        val location = IntArray(2)
        val items = LinkedList<View?>()
        items.add(view)
        while (!items.isEmpty()) {
            val item = items.removeFirst()
            if (item != null && item.visibility == View.VISIBLE) {
                if (item.tag == "exclude") {
                    // skip excluded widgets
                } else if (item is ViewGroup && item.willNotDraw()) {
                    // skip layouts which don't draw anything
                } else {
                    item.getLocationOnScreen(location)
                    val x = location[0].toFloat() + item.translationX
                    val y = location[1].toFloat() + item.translationY

                    val saveCount = canvasDelegate!!.save()
                    recorder.translate(
                        x, y
                    )
                    ViewHelper.executeOnDraw(item, canvasDelegate!!)
                    canvasDelegate!!.restoreToCount(saveCount)
                }

                if (item is ViewGroup) {
                    item.clipChildren
                    val childCount = item.childCount
                    for (i in 0 until childCount) {
                        items.add(item.getChildAt(i))
                    }
                }
            }
        }
    }

}
