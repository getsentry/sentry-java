package io.sentry.android.core.internal.util

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ILogger
import io.sentry.android.core.BuildInfoProvider
import junit.framework.TestCase.assertNull
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric.buildActivity
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPixelCopy
import kotlin.test.Test
import kotlin.test.assertNotNull

@Config(
    shadows = [ShadowPixelCopy::class],
    sdk = [26, 33]
)
@RunWith(AndroidJUnit4::class)
class ScreenshotUtilTest {

    @Test
    fun `when window is null, null is returned`() {
        val activity = mock<Activity>()
        whenever(activity.isFinishing).thenReturn(false)
        whenever(activity.isDestroyed).thenReturn(false)

        val data =
            ScreenshotUtils.takeScreenshot(activity, mock<ILogger>(), mock<BuildInfoProvider>())
        assertNull(data)
    }

    @Test
    fun `when decorView is null, null is returned`() {
        val activity = mock<Activity>()
        whenever(activity.isFinishing).thenReturn(false)
        whenever(activity.isDestroyed).thenReturn(false)
        whenever(activity.window).thenReturn(mock<Window>())

        val data =
            ScreenshotUtils.takeScreenshot(activity, mock<ILogger>(), mock<BuildInfoProvider>())
        assertNull(data)
    }

    @Test
    fun `when root view is null, null is returned`() {
        val activity = mock<Activity>()
        val window = mock<Window>()
        val decorView = mock<View>()
        whenever(activity.isFinishing).thenReturn(false)
        whenever(activity.isDestroyed).thenReturn(false)
        whenever(activity.window).thenReturn(window)

        whenever(window.peekDecorView()).thenReturn(decorView)

        val data =
            ScreenshotUtils.takeScreenshot(activity, mock<ILogger>(), mock<BuildInfoProvider>())
        assertNull(data)
    }

    @Test
    fun `when root view has no size, null is returned`() {
        val activity = mock<Activity>()
        val window = mock<Window>()
        val decorView = mock<View>()
        val rootView = mock<View>()
        whenever(activity.isFinishing).thenReturn(false)
        whenever(activity.isDestroyed).thenReturn(false)
        whenever(activity.window).thenReturn(window)

        whenever(window.peekDecorView()).thenReturn(decorView)
        whenever(decorView.rootView).thenReturn(rootView)

        whenever(rootView.width).thenReturn(0)
        whenever(rootView.height).thenReturn(0)

        val data =
            ScreenshotUtils.takeScreenshot(activity, mock<ILogger>(), mock<BuildInfoProvider>())
        assertNull(data)
    }

    @Test
    fun `capturing screenshots works for Android O using PixelCopy API`() {
        val controller = buildActivity(ExampleActivity::class.java, null).setup()
        controller.create().start().resume()

        val logger = mock<ILogger>()
        val buildInfoProvider = mock<BuildInfoProvider>()
        whenever(buildInfoProvider.sdkInfoVersion).thenReturn(Build.VERSION_CODES.O)

        val data = ScreenshotUtils.takeScreenshot(controller.get(), logger, buildInfoProvider)
        assertNotNull(data)
    }

    @Test
    fun `capturing screenshots works pre Android O using Canvas API`() {
        val controller = buildActivity(ExampleActivity::class.java, null).setup()
        controller.create().start().resume()

        val logger = mock<ILogger>()
        val buildInfoProvider = mock<BuildInfoProvider>()
        whenever(buildInfoProvider.sdkInfoVersion).thenReturn(Build.VERSION_CODES.N)

        val data = ScreenshotUtils.takeScreenshot(controller.get(), logger, buildInfoProvider)
        assertNotNull(data)
    }
}

class ExampleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(View(this))
    }
}
