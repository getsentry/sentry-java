package io.sentry.android.core

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Looper
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.RadioButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dropbox.differ.Color as DifferColor
import com.dropbox.differ.Image
import com.dropbox.differ.SimpleImageComparator
import io.sentry.Attachment
import io.sentry.Hint
import io.sentry.MainEventProcessor
import io.sentry.SentryEvent
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.TypeCheckHint.ANDROID_ACTIVITY
import io.sentry.protocol.SentryException
import io.sentry.util.thread.IThreadChecker
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric.buildActivity
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowPixelCopy

@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowPixelCopy::class], sdk = [30])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotEventProcessorTest {

  companion object {
    /**
     * Set to `true` to record/update golden images for snapshot tests. When `true`, screenshots
     * will be saved to src/test/resources/snapshots/{testName}.png. Set back to `false` after
     * recording to run comparison tests.
     */
    private const val RECORD_SNAPSHOTS = false

    private val SNAPSHOTS_DIR =
      File("src/test/resources/snapshots/ScreenshotEventProcessorTest").also {
        if (RECORD_SNAPSHOTS) it.mkdirs()
      }
  }

  private class Fixture {
    lateinit var activity: Activity
    val threadChecker = mock<IThreadChecker>()
    val options = SentryAndroidOptions().apply { dsn = "https://key@sentry.io/proj" }
    val mainProcessor = MainEventProcessor(options)

    init {
      whenever(threadChecker.isMainThread).thenReturn(true)
    }

    fun getSut(
      attachScreenshot: Boolean = false,
      isReplayAvailable: Boolean = false,
    ): ScreenshotEventProcessor {
      options.isAttachScreenshot = attachScreenshot
      options.threadChecker = threadChecker

      return ScreenshotEventProcessor(options, BuildInfoProvider(options.logger), isReplayAvailable)
    }
  }

  private lateinit var fixture: Fixture

  @BeforeTest
  fun `set up`() {
    System.setProperty("robolectric.areWindowsMarkedVisible", "true")
    System.setProperty("robolectric.pixelCopyRenderMode", "hardware")

    fixture = Fixture()
    CurrentActivityHolder.getInstance().clearActivity()
    fixture.activity = buildActivity(MaskingActivity::class.java, null).setup().get()
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

    fixture.activity.finish()
    CurrentActivityHolder.getInstance().setActivity(fixture.activity)

    val event = fixture.mainProcessor.process(getEvent(), hint)
    sut.process(event, hint)

    assertNull(hint.screenshot)
  }

  @Test
  fun `when view is zeroed, does nothing`() {
    val sut = fixture.getSut(true)
    val hint = Hint()

    val root = fixture.activity.window.decorView
    root.layout(0, 0, 0, 0)
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
  @Config(sdk = [23])
  fun `when screenshot event processor is called from background thread it executes on main thread`() {
    val sut = fixture.getSut(true)
    whenever(fixture.threadChecker.isMainThread).thenReturn(false)

    CurrentActivityHolder.getInstance().setActivity(fixture.activity)

    val hint = Hint()
    val event = fixture.mainProcessor.process(getEvent(), hint)
    sut.process(event, hint)

    shadowOf(Looper.getMainLooper()).idle()
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
    val event = SentryEvent().apply { exceptions = listOf(SentryException()) }
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
    val event = SentryEvent().apply { exceptions = listOf(SentryException()) }
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

    fixture.options.setBeforeScreenshotCaptureCallback { _, _, _ -> true }
    val event = SentryEvent().apply { exceptions = listOf(SentryException()) }
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

    fixture.options.setBeforeScreenshotCaptureCallback { _, _, _ -> false }
    val processor = fixture.getSut(true)

    val event = SentryEvent().apply { exceptions = listOf(SentryException()) }
    val hint = Hint()

    processor.process(event, hint)
    assertNull(hint.screenshot)
  }

  @Test
  fun `when capture callback returns true, a screenshot should be captured`() {
    CurrentActivityHolder.getInstance().setActivity(fixture.activity)

    fixture.options.setBeforeViewHierarchyCaptureCallback { _, _, _ -> true }
    val processor = fixture.getSut(true)

    val event = SentryEvent().apply { exceptions = listOf(SentryException()) }
    val hint = Hint()

    processor.process(event, hint)
    assertNotNull(hint.screenshot)
  }

  @Test
  fun `when masking is configured and VH capture fails, no screenshot is attached`() {
    val sut = fixture.getSut(attachScreenshot = true, isReplayAvailable = true)
    fixture.options.screenshot.setMaskAllText(true)
    val hint = Hint()

    // No activity set, so VH capture will return null (no rootView)
    CurrentActivityHolder.getInstance().clearActivity()

    val event = fixture.mainProcessor.process(getEvent(), hint)
    sut.process(event, hint)

    assertNull(hint.screenshot)
  }

  @Test
  fun `when masking is configured but replay is not available, screenshot is not captured`() {
    val sut = fixture.getSut(attachScreenshot = true, isReplayAvailable = false)
    fixture.options.screenshot.setMaskAllText(true)
    val hint = Hint()

    CurrentActivityHolder.getInstance().setActivity(fixture.activity)

    val event = fixture.mainProcessor.process(getEvent(), hint)
    sut.process(event, hint)

    assertNull(hint.screenshot)
  }

  @Test
  fun `when masking is configured from background thread, VH is captured on main thread`() {
    fixture.options.screenshot.setMaskAllText(true)
    val sut = fixture.getSut(attachScreenshot = true, isReplayAvailable = true)
    whenever(fixture.threadChecker.isMainThread).thenReturn(false)

    CurrentActivityHolder.getInstance().setActivity(fixture.activity)

    val hint = Hint()
    val event = fixture.mainProcessor.process(getEvent(), hint)
    sut.process(event, hint)

    shadowOf(Looper.getMainLooper()).idle()
    assertNotNull(hint.screenshot)
  }

  // region Snapshot Tests

  @Test
  fun `snapshot - screenshot without masking`() {
    val bytes = processEventForSnapshots("screenshot_no_masking", isReplayAvailable = false)
    assertNotNull(bytes)
  }

  @Test
  fun `snapshot - screenshot with text masking enabled`() {
    val bytes =
      processEventForSnapshots("screenshot_mask_text") { it.screenshot.setMaskAllText(true) }
    assertNotNull(bytes)
  }

  @Test
  fun `snapshot - screenshot with image masking enabled`() {
    val bytes =
      processEventForSnapshots("screenshot_mask_images") { it.screenshot.setMaskAllImages(true) }
    assertNotNull(bytes)
  }

  @Test
  fun `snapshot - screenshot with all masking enabled`() {
    val bytes =
      processEventForSnapshots("screenshot_mask_all") {
        it.screenshot.setMaskAllText(true)
        it.screenshot.setMaskAllImages(true)
      }
    assertNotNull(bytes)
  }

  @Test
  fun `snapshot - screenshot with custom view masking`() {
    val bytes =
      processEventForSnapshots("screenshot_mask_custom_view") {
        // CustomView draws white, so masking it should draw black on top
        it.screenshot.addMaskViewClass(CustomView::class.java.name)
      }
    assertNotNull(bytes)
  }

  @Test
  fun `snapshot - screenshot with ellipsized text no masking`() {
    fixture.activity = buildActivity(EllipsizedTextActivity::class.java, null).setup().get()
    val bytes =
      processEventForSnapshots(
        "screenshot_mask_ellipsized_view_unmasked",
        isReplayAvailable = false,
      )
    assertNotNull(bytes)
  }

  @Test
  fun `snapshot - screenshot with ellipsized text masking`() {
    fixture.activity = buildActivity(EllipsizedTextActivity::class.java, null).setup().get()
    val bytes =
      processEventForSnapshots("screenshot_mask_ellipsized_view_masked") {
        it.screenshot.setMaskAllText(true)
      }
    assertNotNull(bytes)
  }

  @Test
  fun `snapshot - compose text no masking`() {
    fixture.activity = buildActivity(ComposeTextActivity::class.java, null).setup().get()
    val bytes =
      processEventForSnapshots(
        "screenshot_mask_ellipsized_compose_unmasked",
        isReplayAvailable = false,
      )
    assertNotNull(bytes)
  }

  @Test
  fun `snapshot - compose text with masking`() {
    fixture.activity = buildActivity(ComposeTextActivity::class.java, null).setup().get()
    val bytes =
      processEventForSnapshots("screenshot_mask_ellipsized_compose_masked") {
        it.screenshot.setMaskAllText(true)
      }
    assertNotNull(bytes)
  }

  // endregion

  private fun getEvent(): SentryEvent = SentryEvent(Throwable("Throwable"))

  /**
   * Helper method for snapshot testing. Processes an event and captures a screenshot, then either
   * saves it as a golden image (when RECORD_SNAPSHOTS=true) or compares it against an existing
   * golden image.
   *
   * @param testName The name used for the golden image file (without extension)
   * @param attachScreenshot Whether to enable screenshot attachment
   * @param isReplayAvailable Whether the replay module is available (enables masking)
   * @param configureOptions Lambda to configure additional options before processing
   * @return The captured screenshot bytes, or null if no screenshot was captured
   */
  private fun processEventForSnapshots(
    testName: String,
    attachScreenshot: Boolean = true,
    isReplayAvailable: Boolean = true,
    configureOptions: (SentryAndroidOptions) -> Unit = {},
  ): ByteArray? {
    configureOptions(fixture.options)
    val sut = fixture.getSut(attachScreenshot, isReplayAvailable)
    val hint = Hint()

    CurrentActivityHolder.getInstance().setActivity(fixture.activity)

    val event = fixture.mainProcessor.process(getEvent(), hint)
    sut.process(event, hint)

    val screenshot = hint.screenshot ?: return null
    val bytes = screenshot.bytes ?: screenshot.byteProvider?.call() ?: return null

    val snapshotFile = File(SNAPSHOTS_DIR, "$testName.png")
    if (RECORD_SNAPSHOTS) {
      snapshotFile.writeBytes(bytes)
      println("Recorded snapshot: ${snapshotFile.absolutePath}")
    } else if (snapshotFile.exists()) {
      val expectedBitmap = BitmapFactory.decodeFile(snapshotFile.absolutePath)
      val actualBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

      val result =
        SimpleImageComparator(maxDistance = 0.01f)
          .compare(BitmapImage(expectedBitmap), BitmapImage(actualBitmap))
      assertEquals(
        0,
        result.pixelDifferences,
        "Screenshot does not match golden image: ${snapshotFile.absolutePath}. " +
          "Pixel differences: ${result.pixelDifferences}",
      )
    }

    return bytes
  }

  /** Adapter to wrap Android Bitmap for use with dropbox/differ library */
  private class BitmapImage(private val bitmap: Bitmap) : Image {
    override val height: Int
      get() = bitmap.height

    override val width: Int
      get() = bitmap.width

    override fun getPixel(x: Int, y: Int): DifferColor = DifferColor(bitmap.getPixel(x, y))
  }
}

private class CustomView(context: Context) : View(context) {
  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    canvas.drawColor(Color.WHITE)
  }
}

private class EllipsizedTextActivity : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val longText = "This is a very long text that should be ellipsized when it does not fit"

    val linearLayout =
      LinearLayout(this).apply {
        setBackgroundColor(Color.WHITE)
        orientation = LinearLayout.VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setPadding(10, 10, 10, 10)
      }

    // Ellipsize end
    linearLayout.addView(
      TextView(this).apply {
        text = longText
        setTextColor(Color.BLACK)
        textSize = 16f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setBackgroundColor(Color.LTGRAY)
        layoutParams =
          LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 8, 0, 0)
          }
      }
    )

    // Ellipsize middle
    linearLayout.addView(
      TextView(this).apply {
        text = longText
        setTextColor(Color.BLACK)
        textSize = 16f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.MIDDLE
        setBackgroundColor(Color.LTGRAY)
        layoutParams =
          LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 8, 0, 0)
          }
      }
    )

    // Ellipsize start
    linearLayout.addView(
      TextView(this).apply {
        text = longText
        setTextColor(Color.BLACK)
        textSize = 16f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.START
        setBackgroundColor(Color.LTGRAY)
        layoutParams =
          LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 8, 0, 0)
          }
      }
    )

    // Non-ellipsized text for comparison
    linearLayout.addView(
      TextView(this).apply {
        text = "Short text"
        setTextColor(Color.BLACK)
        textSize = 16f
        setBackgroundColor(Color.LTGRAY)
        layoutParams =
          LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 8, 0, 0)
          }
      }
    )

    setContentView(linearLayout)
  }
}

private class ComposeTextActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val longText = "This is a very long text that should be ellipsized when it does not fit in view"

    setContent {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color.White)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // Ellipsis overflow
        Text(
          longText,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          fontSize = 16.sp,
          modifier =
            Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.LightGray),
        )

        // Text with textAlign center
        Text(
          "Centered text",
          textAlign = TextAlign.Center,
          fontSize = 16.sp,
          modifier =
            Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.LightGray),
        )

        // Text with textAlign end
        Text(
          "End-aligned text",
          textAlign = TextAlign.End,
          fontSize = 16.sp,
          modifier =
            Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.LightGray),
        )

        // Ellipsis with textAlign center
        Text(
          longText,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
          fontSize = 16.sp,
          modifier =
            Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.LightGray),
        )

        // Weighted row with text
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            "Weight 1",
            fontSize = 16.sp,
            modifier = Modifier.weight(1f).background(androidx.compose.ui.graphics.Color.LightGray),
          )
          Text(
            "Weight 1",
            fontSize = 16.sp,
            modifier = Modifier.weight(1f).background(androidx.compose.ui.graphics.Color.LightGray),
          )
        }

        // Weighted row with ellipsized text
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            longText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f).background(androidx.compose.ui.graphics.Color.LightGray),
          )
          Text(
            longText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f).background(androidx.compose.ui.graphics.Color.LightGray),
          )
        }

        // Short text (for comparison)
        Text(
          "Short text",
          fontSize = 16.sp,
          modifier = Modifier.background(androidx.compose.ui.graphics.Color.LightGray),
        )
      }
    }
  }
}

private class MaskingActivity : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val linearLayout =
      LinearLayout(this).apply {
        setBackgroundColor(android.R.color.white)
        orientation = LinearLayout.VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
      }

    val textView =
      TextView(this).apply {
        text = "Hello, World!"
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
      }
    linearLayout.addView(textView)

    val image = this::class.java.classLoader?.getResource("Tongariro.jpg")!!
    val imageView =
      ImageView(this).apply {
        setImageDrawable(Drawable.createFromPath(image.path))
        layoutParams = LayoutParams(50, 50).apply { setMargins(0, 16, 0, 0) }
      }
    linearLayout.addView(imageView)

    val radioButton =
      RadioButton(this).apply {
        text = "Radio Button"
        layoutParams =
          LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 16, 0, 0)
          }
      }
    linearLayout.addView(radioButton)

    val customView =
      CustomView(this).apply {
        layoutParams = LayoutParams(50, 50).apply { setMargins(0, 16, 0, 0) }
      }
    linearLayout.addView(customView)

    setContentView(linearLayout)
  }
}
