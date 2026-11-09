package io.sentry.android.core.internal.util

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.android.core.BuildInfoProvider
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.util.thread.IThreadChecker
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric.buildActivity
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowPixelCopy

@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowPixelCopy::class], sdk = [30])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotUtilsMaskedScreenshotTest {

  private class Fixture {
    lateinit var activity: Activity
    val threadChecker = mock<IThreadChecker>()
    val options =
      SentryAndroidOptions().apply {
        dsn = "https://key@sentry.io/proj"
        threadChecker = this@Fixture.threadChecker
        // Tests opt in to masking, so they also run without sentry-android-replay.
        screenshot.setMaskAllText(false)
        screenshot.setMaskAllImages(false)
      }
    val buildInfoProvider = BuildInfoProvider(options.logger)

    init {
      whenever(threadChecker.isMainThread).thenReturn(true)
    }
  }

  private lateinit var fixture: Fixture

  private fun takeMaskedScreenshot(isReplayAvailable: Boolean): ByteArray? =
    ScreenshotUtils.compressBitmapToPng(
      ScreenshotUtils.captureMaskedScreenshot(
        fixture.activity,
        fixture.options,
        fixture.buildInfoProvider,
        isReplayAvailable,
      ),
      fixture.options.logger,
    )

  @BeforeTest
  fun `set up`() {
    System.setProperty("robolectric.areWindowsMarkedVisible", "true")
    System.setProperty("robolectric.pixelCopyRenderMode", "hardware")

    fixture = Fixture()
    fixture.activity = buildActivity(TextActivity::class.java, null).setup().get()
  }

  @Test
  fun `when masking is not configured, returns an unmasked screenshot`() {
    val bytes = takeMaskedScreenshot(isReplayAvailable = false)

    assertNotNull(bytes)
  }

  @Test
  fun `when masking is configured, returns a masked screenshot`() {
    val unmasked = takeMaskedScreenshot(isReplayAvailable = true)
    fixture.options.screenshot.setMaskAllText(true)
    val masked = takeMaskedScreenshot(isReplayAvailable = true)

    assertNotNull(unmasked)
    assertNotNull(masked)
    assertFalse(unmasked.contentEquals(masked))
  }

  @Test
  fun `when masking is configured but replay is not available, returns no screenshot`() {
    fixture.options.screenshot.setMaskAllText(true)

    val bytes = takeMaskedScreenshot(isReplayAvailable = false)

    assertNull(bytes)
  }

  @Test
  fun `when masking is configured but fails, returns no screenshot`() {
    fixture.options.screenshot.setMaskAllText(true)
    fixture.activity.findViewById<ThrowingTextView>(android.R.id.text1).throwOnTraversal = true

    val bytes = takeMaskedScreenshot(isReplayAvailable = true)

    assertNull(bytes)
  }

  @Test
  fun `public overload detects replay availability`() {
    fixture.options.screenshot.setMaskAllText(true)

    // sentry-android-replay is on the test classpath
    val bytes =
      ScreenshotUtils.takeMaskedScreenshot(
        fixture.activity,
        fixture.options,
        fixture.buildInfoProvider,
      )

    assertNotNull(bytes)
  }
}

private class TextActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val linearLayout =
      LinearLayout(this).apply {
        setBackgroundColor(Color.WHITE)
        orientation = LinearLayout.VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
      }
    linearLayout.addView(
      ThrowingTextView(this).apply {
        id = android.R.id.text1
        text = "Sensitive text"
        setTextColor(Color.BLACK)
        textSize = 32f
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
      }
    )
    setContentView(linearLayout)
  }
}

/** Fails view hierarchy traversal on demand, to exercise the masking failure path. */
private class ThrowingTextView(context: Context) : TextView(context) {
  var throwOnTraversal = false

  override fun getTotalPaddingLeft(): Int {
    if (throwOnTraversal) {
      throw IllegalStateException("Traversal failure")
    }
    return super.getTotalPaddingLeft()
  }
}
