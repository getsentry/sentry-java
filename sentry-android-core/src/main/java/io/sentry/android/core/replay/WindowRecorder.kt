package io.sentry.android.core.replay

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.CanvasDelegate
import android.graphics.RenderNode
import android.graphics.RenderNodeHelper
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import io.sentry.DateUtils
import io.sentry.Hint
import io.sentry.ReplayRecording
import io.sentry.Sentry
import io.sentry.SentryReplayEvent
import java.util.LinkedList

class WindowRecorder : Window.OnFrameMetricsAvailableListener {

    companion object {
        private const val TAG = "WindowRecorder"
        private const val MIN_TIME_BETWEEN_FRAMES_MS = 500
    }

    val recorder = RRWebRecorder()
    private var canvasDelegate: CanvasDelegate? = null
    private var canvas: Canvas? = null
    private var activity: Activity? = null
    private var lastCapturedAtMs: Long? = null

    fun startRecording(activity: Activity) {
        this.activity = activity

        activity.window.addOnFrameMetricsAvailableListener(this, Handler(Looper.getMainLooper()))
        activity.window.callback = object : WindowCallbackDelegate(activity.window.callback) {

            override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
                event?.let {
                    val timestamp = System.currentTimeMillis()
                    recorder.onTouchEvent(timestamp, event)
                }
                return super.dispatchTouchEvent(event)
            }
        }
    }

    fun stopRecording(): Pair<SentryReplayEvent, Hint> {
        activity?.window?.removeOnFrameMetricsAvailableListener(this)
        activity = null

        Sentry.getCurrentHub().configureScope { scope ->
            scope.breadcrumbs.forEach { breadcrumb ->
                if (breadcrumb.timestamp.time > recorder.startTimeMs &&
                    breadcrumb.timestamp.time < recorder.endTimeMs &&
                    breadcrumb.category == "ui.click"
                ) {
                    recorder.addBreadcrumb(breadcrumb)
                }
            }
        }
        val replayEvent = SentryReplayEvent().apply {
            timestamp =
                DateUtils.millisToSeconds(recorder.endTimeMs.toDouble())
            replayStartTimestamp =
                DateUtils.millisToSeconds(recorder.startTimeMs.toDouble())
            segmentId = 0
        }

        val replayRecording = ReplayRecording().apply {
            segmentId = 0
            payload = recorder.recording as List<Any>
        }

        val hint = Hint()
        hint.addReplayRecording(replayRecording)

        return Pair(replayEvent, hint)
    }

    override fun onFrameMetricsAvailable(
        window: Window?,
        frameMetrics: FrameMetrics?,
        dropCountSinceLastInvocation: Int
    ) {
        val view = activity?.window?.decorView
        val renderNodeField = View::class.java.getDeclaredField("mRenderNode")
        renderNodeField.isAccessible = true
        val renderNode = renderNodeField.get(view)
        val nativeRenderNodeField = RenderNode::class.java.getDeclaredField("mNativeRenderNode")
        nativeRenderNodeField.isAccessible = true
        val nativeRenderNode = nativeRenderNodeField.get(renderNode) as Long
        RenderNodeHelper.fetchDisplayList(nativeRenderNode)
//        val renderNode = RenderNodeHelper("replay_node")
//        val displayMetrics = activity?.resources?.displayMetrics
//        renderNode.setPosition(0, 0, displayMetrics!!.widthPixels, displayMetrics!!.heightPixels)
//        val recordingCanvas = renderNode.beginRecording()
//        view?.draw(recordingCanvas)
//        renderNode.endRecording()
//        view?.let {
//            captureFrame(it)
//        }
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
//        canvas!!.restoreToCount(1)
        recorder.beginFrame(System.currentTimeMillis(), view.width, view.height)

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
                    val renderNodeField = View::class.java.getDeclaredField("mRenderNode")
                    renderNodeField.isAccessible = true
                    val renderNode = renderNodeField.get(item)
                    val outputMethod = RenderNode::class.java.getDeclaredMethod("output")
                    outputMethod.isAccessible = true
                    outputMethod.invoke(renderNode)
//                    item.getLocationOnScreen(location)
//                    val x = location[0].toFloat() + item.translationX
//                    val y = location[1].toFloat() + item.translationY
//
//                    val saveCount = canvasDelegate!!.save()
//                    recorder.translate(
//                        x,
//                        y
//                    )
//                    ViewHelper.executeOnDraw(item, canvasDelegate!!)
//                    canvasDelegate!!.restoreToCount(saveCount)
                }

                if (item is ViewGroup) {
                    val childCount = item.childCount
                    for (i in 0 until childCount) {
                        items.add(item.getChildAt(i))
                    }
                }
            }
        }
    }
}
