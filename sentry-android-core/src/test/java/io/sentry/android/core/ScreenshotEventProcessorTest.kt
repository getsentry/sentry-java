package io.sentry.android.core

import android.app.Activity
import android.view.View
import android.view.Window
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Attachment
import io.sentry.Hint
import io.sentry.MainEventProcessor
import io.sentry.SentryEvent
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.TypeCheckHint.ANDROID_ACTIVITY
import io.sentry.protocol.SentryException
import io.sentry.util.thread.IThreadChecker
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ScreenshotEventProcessorTest {

    private class Fixture {
        val buildInfo = mock<BuildInfoProvider>()
        val activity = mock<Activity>()
        val window = mock<Window>()
        val view = mock<View>()
        val rootView = mock<View>()
        val threadChecker = mock<IThreadChecker>()
        val options = SentryAndroidOptions().apply {
            dsn = "https://key@sentry.io/proj"
        }
        val mainProcessor = MainEventProcessor(options)

        init {
            whenever(rootView.width).thenReturn(1)
            whenever(rootView.height).thenReturn(1)
            whenever(view.rootView).thenReturn(rootView)
            whenever(window.decorView).thenReturn(view)
            whenever(window.peekDecorView()).thenReturn(view)
            whenever(activity.window).thenReturn(window)
            whenever(activity.runOnUiThread(any())).then {
                it.getArgument<Runnable>(0).run()
            }

            whenever(threadChecker.isMainThread).thenReturn(true)
        }

        fun getSut(attachScreenshot: Boolean = false): ScreenshotEventProcessor {
            options.isAttachScreenshot = attachScreenshot
            options.threadChecker = threadChecker

            return ScreenshotEventProcessor(options, buildInfo)
        }
    }

    private lateinit var fixture: Fixture

    @BeforeTest
    fun `set up`() {
        fixture = Fixture()
        CurrentActivityHolder.getInstance().clearActivity()
    }

    @Test
    fun `when process is called and attachScreenshot is disabled, does nothing`() {
        val sut = fixture.getSut()
        val hint = Hint()

        CurrentActivityHolder.getInstance().setActivity(fixture.activity)

        val event = fixture.mainProcessor.process(getEvent(), hint)
        sut.process(event, hint)

        assertNull(hint.screenshot)
    }

    @Test
    fun `when event is not errored, does nothing`() {
        val sut = fixture.getSut(true)
        val hint = Hint()

        CurrentActivityHolder.getInstance().setActivity(fixture.activity)

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
        CurrentActivityHolder.getInstance().setActivity(fixture.activity)

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
        CurrentActivityHolder.getInstance().setActivity(fixture.activity)

        val event = fixture.mainProcessor.process(getEvent(), hint)
        sut.process(event, hint)

        assertNull(hint.screenshot)
    }

    @Test
    fun `when process is called and attachScreenshot is enabled, add attachment to hints`() {
        val sut = fixture.getSut(true)
        val hint = Hint()

        CurrentActivityHolder.getInstance().setActivity(fixture.activity)

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

        CurrentActivityHolder.getInstance().setActivity(fixture.activity)
        CurrentActivityHolder.getInstance().clearActivity()

        val event = fixture.mainProcessor.process(getEvent(), hint)
        sut.process(event, hint)

        assertNull(hint.screenshot)
    }

    @Test
    fun `when screenshot event processor is called from background thread it executes on main thread`() {
        val sut = fixture.getSut(true)
        whenever(fixture.threadChecker.isMainThread).thenReturn(false)

        CurrentActivityHolder.getInstance().setActivity(fixture.activity)

        val hint = Hint()
        val event = fixture.mainProcessor.process(getEvent(), hint)
        sut.process(event, hint)

        verify(fixture.activity).runOnUiThread(any())
        assertNotNull(hint.screenshot)
    }

    fun `when enabled, the feature is added to the integration list`() {
        SentryIntegrationPackageStorage.getInstance().clearStorage()
        val hint = Hint()
        val sut = fixture.getSut(true)
        val event = fixture.mainProcessor.process(getEvent(), hint)
        sut.process(event, hint)
        assertTrue(fixture.options.sdkVersion!!.integrationSet.contains("Screenshot"))
    }

    @Test
    fun `when not enabled, the feature is not added to the integration list`() {
        SentryIntegrationPackageStorage.getInstance().clearStorage()
        val hint = Hint()
        val sut = fixture.getSut(false)
        val event = fixture.mainProcessor.process(getEvent(), hint)
        sut.process(event, hint)
        assertFalse(fixture.options.sdkVersion!!.integrationSet.contains("Screenshot"))
    }

    @Test
    fun `when screenshots are captured rapidly, capturing should be debounced`() {
        CurrentActivityHolder.getInstance().setActivity(fixture.activity)

        val processor = fixture.getSut(true)
        val event = SentryEvent().apply {
            exceptions = listOf(SentryException())
        }
        var hint0 = Hint()
        processor.process(event, hint0)
        assertNotNull(hint0.screenshot)
        hint0 = Hint()
        processor.process(event, hint0)
        assertNotNull(hint0.screenshot)
        hint0 = Hint()
        processor.process(event, hint0)
        assertNotNull(hint0.screenshot)

        val hint1 = Hint()
        processor.process(event, hint1)
        assertNull(hint1.screenshot)
    }

    @Test
    fun `when screenshots are captured rapidly, debounce flag should be propagated`() {
        CurrentActivityHolder.getInstance().setActivity(fixture.activity)

        var debounceFlag = false
        fixture.options.setBeforeScreenshotCaptureCallback { _, _, debounce ->
            debounceFlag = debounce
            true
        }

        val processor = fixture.getSut(true)
        val event = SentryEvent().apply {
            exceptions = listOf(SentryException())
        }
        val hint0 = Hint()
        processor.process(event, hint0)
        assertFalse(debounceFlag)
        processor.process(event, hint0)
        assertFalse(debounceFlag)
        processor.process(event, hint0)
        assertFalse(debounceFlag)

        val hint1 = Hint()
        processor.process(event, hint1)
        assertTrue(debounceFlag)
    }

    @Test
    fun `when screenshots are captured rapidly, capture callback can still overrule debouncing`() {
        CurrentActivityHolder.getInstance().setActivity(fixture.activity)

        val processor = fixture.getSut(true)

        fixture.options.setBeforeScreenshotCaptureCallback { _, _, _ ->
            true
        }
        val event = SentryEvent().apply {
            exceptions = listOf(SentryException())
        }
        val hint0 = Hint()
        processor.process(event, hint0)
        processor.process(event, hint0)
        processor.process(event, hint0)
        assertNotNull(hint0.screenshot)

        val hint1 = Hint()
        processor.process(event, hint1)
        assertNotNull(hint1.screenshot)
    }

    @Test
    fun `when capture callback returns false, no screenshot should be captured`() {
        CurrentActivityHolder.getInstance().setActivity(fixture.activity)

        fixture.options.setBeforeScreenshotCaptureCallback { _, _, _ ->
            false
        }
        val processor = fixture.getSut(true)

        val event = SentryEvent().apply {
            exceptions = listOf(SentryException())
        }
        val hint = Hint()

        processor.process(event, hint)
        assertNull(hint.screenshot)
    }

    @Test
    fun `when capture callback returns true, a screenshot should be captured`() {
        CurrentActivityHolder.getInstance().setActivity(fixture.activity)

        fixture.options.setBeforeViewHierarchyCaptureCallback { _, _, _ ->
            true
        }
        val processor = fixture.getSut(true)

        val event = SentryEvent().apply {
            exceptions = listOf(SentryException())
        }
        val hint = Hint()

        processor.process(event, hint)
        assertNotNull(hint.screenshot)
    }

    private fun getEvent(): SentryEvent = SentryEvent(Throwable("Throwable"))
}
