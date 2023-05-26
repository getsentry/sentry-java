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
import io.sentry.util.thread.IMainThreadChecker
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
        val mainThreadChecker = mock<IMainThreadChecker>()
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
            whenever(activity.runOnUiThread(any())).then {
                it.getArgument<Runnable>(0).run()
            }

            whenever(mainThreadChecker.isMainThread).thenReturn(true)
        }

        fun getSut(attachScreenshot: Boolean = false): ScreenshotEventProcessor {
            options.isAttachScreenshot = attachScreenshot
            options.mainThreadChecker = mainThreadChecker

            return ScreenshotEventProcessor(options, buildInfo)
        }
    }

    private lateinit var fixture: Fixture

    @BeforeTest
    fun `set up`() {
        fixture = Fixture()
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
        whenever(fixture.mainThreadChecker.isMainThread).thenReturn(false)

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

    private fun getEvent(): SentryEvent = SentryEvent(Throwable("Throwable"))
}
