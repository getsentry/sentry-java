package io.sentry.android.core

import android.app.Activity
import android.app.Application
import android.view.View
import android.view.Window
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.Attachment
import io.sentry.Hint
import io.sentry.MainEventProcessor
import io.sentry.SentryEvent
import io.sentry.TypeCheckHint.ANDROID_ACTIVITY
import org.junit.runner.RunWith
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ScreenshotEventProcessorTest {

    private class Fixture {
        val application = mock<Application>()
        val buildInfo = mock<BuildInfoProvider>()
        val activity = mock<Activity>()
        val window = mock<Window>()
        val view = mock<View>()
        val rootView = mock<View>()
        val options = SentryAndroidOptions().apply {
            dsn = "https://key@sentry.io/proj"
        }
        val mainProcessor = MainEventProcessor(options)

        init {
            whenever(rootView.width).thenReturn(1)
            whenever(rootView.height).thenReturn(1)
            whenever(view.rootView).thenReturn(rootView)
            whenever(window.decorView).thenReturn(view)
            whenever(activity.window).thenReturn(window)
        }

        fun getSut(attachScreenshot: Boolean = false): ScreenshotEventProcessor {
            options.isAttachScreenshot = attachScreenshot

            return ScreenshotEventProcessor(application, options, buildInfo)
        }
    }

    private lateinit var fixture: Fixture

    @BeforeTest
    fun `set up`() {
        fixture = Fixture()
    }

    @Test
    fun `when adding screenshot event processor, registerActivityLifecycleCallbacks`() {
        fixture.getSut()

        verify(fixture.application).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `when close is called and attach screenshot is enabled, unregisterActivityLifecycleCallbacks`() {
        val sut = fixture.getSut(true)

        sut.close()

        verify(fixture.application).unregisterActivityLifecycleCallbacks(any())
    }

    @Test
    fun `when close is called and  attach screenshot is disabled, does not unregisterActivityLifecycleCallbacks`() {
        val sut = fixture.getSut()

        sut.close()

        verify(fixture.application, never()).unregisterActivityLifecycleCallbacks(any())
    }

    @Test
    fun `when process is called and attachScreenshot is disabled, unregisterActivityLifecycleCallbacks`() {
        val sut = fixture.getSut()
        val hint = Hint()

        val event = fixture.mainProcessor.process(getEvent(), hint)
        sut.process(event, hint)

        verify(fixture.application).unregisterActivityLifecycleCallbacks(any())
    }

    @Test
    fun `when process is called and attachScreenshot is disabled, does nothing`() {
        val sut = fixture.getSut()
        val hint = Hint()

        sut.onActivityCreated(fixture.activity, null)

        val event = fixture.mainProcessor.process(getEvent(), hint)
        sut.process(event, hint)

        assertNull(hint.screenshot)
    }

    @Test
    fun `when event is not errored, does nothing`() {
        val sut = fixture.getSut(true)
        val hint = Hint()

        sut.onActivityCreated(fixture.activity, null)

        val event = fixture.mainProcessor.process(SentryEvent(), hint)
        sut.process(event, hint)

        assertNull(hint.screenshot)
    }

    @Test
    fun `when there is not activity, does nothing`() {
        val sut = fixture.getSut(true)
        val hint = Hint()

        val event = fixture.mainProcessor.process(getEvent(), hint)
        sut.process(event, hint)

        assertNull(hint.screenshot)
    }

    @Test
    fun `when activity is finishing, does nothing`() {
        val sut = fixture.getSut(true)
        val hint = Hint()

        whenever(fixture.activity.isFinishing).thenReturn(true)
        sut.onActivityCreated(fixture.activity, null)

        val event = fixture.mainProcessor.process(getEvent(), hint)
        sut.process(event, hint)

        assertNull(hint.screenshot)
    }

    @Test
    fun `when view is zeroed, does nothing`() {
        val sut = fixture.getSut(true)
        val hint = Hint()

        whenever(fixture.rootView.width).thenReturn(0)
        whenever(fixture.rootView.height).thenReturn(0)
        sut.onActivityCreated(fixture.activity, null)

        val event = fixture.mainProcessor.process(getEvent(), hint)
        sut.process(event, hint)

        assertNull(hint.screenshot)
    }

    @Test
    fun `when process is called and attachScreenshot is enabled, add attachment to hints`() {
        val sut = fixture.getSut(true)
        val hint = Hint()

        sut.onActivityCreated(fixture.activity, null)

        val event = fixture.mainProcessor.process(getEvent(), hint)
        sut.process(event, hint)

        val screenshot = hint.screenshot
        assertTrue(screenshot is Attachment)
        assertEquals("screenshot.png", screenshot.filename)
        assertEquals("image/png", screenshot.contentType)

        assertSame(fixture.activity, hint[ANDROID_ACTIVITY])
    }

    @Test
    fun `when activity is destroyed, does nothing`() {
        val sut = fixture.getSut(true)
        val hint = Hint()

        sut.onActivityCreated(fixture.activity, null)
        sut.onActivityDestroyed(fixture.activity)

        val event = fixture.mainProcessor.process(getEvent(), hint)
        sut.process(event, hint)

        assertNull(hint.screenshot)
    }

    private fun getEvent(): SentryEvent = SentryEvent(Throwable("Throwable"))
}
