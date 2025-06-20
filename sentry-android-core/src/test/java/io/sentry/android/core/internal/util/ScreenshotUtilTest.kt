package io.sentry.android.core.internal.util

import android.app.Activity
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ILogger
import io.sentry.NoOpLogger
import io.sentry.android.core.BuildInfoProvider
import junit.framework.TestCase.assertNull
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric.buildActivity
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPixelCopy

@Config(shadows = [ShadowPixelCopy::class], sdk = [26, 33])
@RunWith(AndroidJUnit4::class)
class ScreenshotUtilTest {
  @Test
  fun `when window is null, null is returned`() {
    val activity = mock<Activity>()
    whenever(activity.isFinishing).thenReturn(false)
    whenever(activity.isDestroyed).thenReturn(false)

    val data =
      ScreenshotUtils.captureScreenshot(activity, mock<ILogger>(), mock<BuildInfoProvider>())
    assertNull(data)
  }

  @Test
  fun `when decorView is null, null is returned`() {
    val activity = mock<Activity>()
    whenever(activity.isFinishing).thenReturn(false)
    whenever(activity.isDestroyed).thenReturn(false)
    whenever(activity.window).thenReturn(mock<Window>())

    val data =
      ScreenshotUtils.captureScreenshot(activity, mock<ILogger>(), mock<BuildInfoProvider>())
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
      ScreenshotUtils.captureScreenshot(activity, mock<ILogger>(), mock<BuildInfoProvider>())
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
      ScreenshotUtils.captureScreenshot(activity, mock<ILogger>(), mock<BuildInfoProvider>())
    assertNull(data)
  }

  @Test
  fun `capturing screenshots works for Android O using PixelCopy API`() {
    val controller = buildActivity(ExampleActivity::class.java, null).setup()
    controller.create().start().resume()

    val logger = mock<ILogger>()
    val buildInfoProvider = mock<BuildInfoProvider>()
    whenever(buildInfoProvider.sdkInfoVersion).thenReturn(Build.VERSION_CODES.O)

    val data = ScreenshotUtils.captureScreenshot(controller.get(), logger, buildInfoProvider)
    assertNotNull(data)
  }

  @Test
  fun `capturing screenshots works pre Android O using Canvas API`() {
    val controller = buildActivity(ExampleActivity::class.java, null).setup()
    controller.create().start().resume()

    val logger = mock<ILogger>()
    val buildInfoProvider = mock<BuildInfoProvider>()
    whenever(buildInfoProvider.sdkInfoVersion).thenReturn(Build.VERSION_CODES.N)

    val data = ScreenshotUtils.captureScreenshot(controller.get(), logger, buildInfoProvider)
    assertNotNull(data)
  }

  @Test
  fun `a null bitmap compresses into null`() {
    val bytes = ScreenshotUtils.compressBitmapToPng(null, NoOpLogger.getInstance())
    assertNull(bytes)
  }

  @Test
  fun `a recycled bitmap compresses into null`() {
    val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    bitmap.recycle()

    val bytes = ScreenshotUtils.compressBitmapToPng(bitmap, NoOpLogger.getInstance())
    assertNull(bytes)
  }

  @Test
  fun `a valid bitmap compresses into a valid bytearray`() {
    val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    val bytes = ScreenshotUtils.compressBitmapToPng(bitmap, NoOpLogger.getInstance())
    assertNotNull(bytes)
    assertTrue(bytes.isNotEmpty())
  }

  @Test
  fun `compressBitmapToPng recycles the supplied bitmap`() {
    val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    assertFalse(bitmap.isRecycled)
    ScreenshotUtils.compressBitmapToPng(bitmap, NoOpLogger.getInstance())
    assertTrue(bitmap.isRecycled)
  }
}

class ExampleActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(View(this))
  }
}
