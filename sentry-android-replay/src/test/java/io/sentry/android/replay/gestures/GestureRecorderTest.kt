package io.sentry.android.replay.gestures

import android.R
import android.app.Activity
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.SentryOptions
import io.sentry.android.core.internal.gestures.NoOpWindowCallback
import io.sentry.android.replay.gestures.GestureRecorder.SentryReplayGestureRecorder
import io.sentry.android.replay.phoneWindow
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class GestureRecorderTest {
    internal class Fixture {

        val options = SentryOptions()

        fun getSut(
            touchRecorderCallback: TouchRecorderCallback = NoOpTouchRecorderCallback()
        ): GestureRecorder {
            return GestureRecorder(options, touchRecorderCallback)
        }
    }

    private val fixture = Fixture()
    private class NoOpTouchRecorderCallback : TouchRecorderCallback {
        override fun onTouchEvent(event: MotionEvent) = Unit
    }

    @Test
    fun `when new window added and window callback is already wrapped, does not wrap it again`() {
        val activity = Robolectric.buildActivity(TestActivity::class.java).setup().get()
        val gestureRecorder = fixture.getSut()

        activity.root.phoneWindow?.callback = SentryReplayGestureRecorder(fixture.options, null, null)
        gestureRecorder.onRootViewsChanged(activity.root, true)

        assertFalse((activity.root.phoneWindow?.callback as SentryReplayGestureRecorder).delegate is SentryReplayGestureRecorder)
    }

    @Test
    fun `when new window added tracks touch events`() {
        var called = false
        val activity = Robolectric.buildActivity(TestActivity::class.java).setup().get()
        val motionEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
        val gestureRecorder = fixture.getSut(
            touchRecorderCallback = object : TouchRecorderCallback {
                override fun onTouchEvent(event: MotionEvent) {
                    assertEquals(MotionEvent.ACTION_DOWN, event.action)
                    called = true
                }
            }
        )

        gestureRecorder.onRootViewsChanged(activity.root, true)

        activity.root.phoneWindow?.callback?.dispatchTouchEvent(motionEvent)
        assertTrue(called)
    }

    @Test
    fun `when window removed and window is not sentry recorder does nothing`() {
        val activity = Robolectric.buildActivity(TestActivity::class.java).setup().get()
        val gestureRecorder = fixture.getSut()

        activity.root.phoneWindow?.callback = NoOpWindowCallback()
        gestureRecorder.onRootViewsChanged(activity.root, false)

        assertTrue(activity.root.phoneWindow?.callback is NoOpWindowCallback)
    }

    @Test
    fun `when window removed stops tracking touch events`() {
        val activity = Robolectric.buildActivity(TestActivity::class.java).setup().get()
        val gestureRecorder = fixture.getSut()

        gestureRecorder.onRootViewsChanged(activity.root, true)
        gestureRecorder.onRootViewsChanged(activity.root, false)

        assertFalse(activity.root.phoneWindow?.callback is SentryReplayGestureRecorder)
    }

    @Test
    fun `when stopped stops tracking all windows`() {
        val activity1 = Robolectric.buildActivity(TestActivity::class.java).setup().get()
        val activity2 = Robolectric.buildActivity(TestActivity2::class.java).setup().get()
        val gestureRecorder = fixture.getSut()

        gestureRecorder.onRootViewsChanged(activity1.root, true)
        gestureRecorder.onRootViewsChanged(activity2.root, true)
        gestureRecorder.stop()

        assertFalse(activity1.root.phoneWindow?.callback is SentryReplayGestureRecorder)
        assertFalse(activity2.root.phoneWindow?.callback is SentryReplayGestureRecorder)
    }
}

private class TestActivity : Activity() {
    lateinit var root: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Holo_Light)
        root = LinearLayout(this)
        setContentView(root)
        actionBar!!.setIcon(R.drawable.ic_lock_power_off)
    }
}

private class TestActivity2 : Activity() {
    lateinit var root: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Holo_Light)
        root = LinearLayout(this)
        setContentView(root)
        actionBar!!.setIcon(R.drawable.ic_lock_power_off)
    }
}
