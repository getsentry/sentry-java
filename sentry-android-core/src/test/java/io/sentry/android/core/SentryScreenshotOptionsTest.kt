package io.sentry.android.core

import io.sentry.SentryMaskingOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SentryScreenshotOptionsTest {

  @Test
  fun `maskViewClasses is empty by default`() {
    val options = SentryScreenshotOptions()
    assertTrue(options.maskViewClasses.isEmpty())
  }

  @Test
  fun `unmaskViewClasses is empty by default`() {
    val options = SentryScreenshotOptions()
    assertTrue(options.unmaskViewClasses.isEmpty())
  }

  @Test
  fun `setMaskAllText true only adds TextView`() {
    val options = SentryScreenshotOptions()
    options.setMaskAllText(true)

    assertTrue(options.maskViewClasses.contains(SentryMaskingOptions.TEXT_VIEW_CLASS_NAME))
    // Should NOT add sensitive view classes (only setMaskAllImages does that)
    assertFalse(options.maskViewClasses.contains(SentryMaskingOptions.WEB_VIEW_CLASS_NAME))
    assertFalse(options.maskViewClasses.contains(SentryMaskingOptions.VIDEO_VIEW_CLASS_NAME))
    assertEquals(1, options.maskViewClasses.size)
  }

  @Test
  fun `setMaskAllImages true adds ImageView and sensitive view classes`() {
    val options = SentryScreenshotOptions()
    options.setMaskAllImages(true)

    assertTrue(options.maskViewClasses.contains(SentryMaskingOptions.IMAGE_VIEW_CLASS_NAME))
    assertTrue(options.maskViewClasses.contains(SentryMaskingOptions.WEB_VIEW_CLASS_NAME))
    assertTrue(options.maskViewClasses.contains(SentryMaskingOptions.VIDEO_VIEW_CLASS_NAME))
    assertTrue(
      options.maskViewClasses.contains(SentryMaskingOptions.ANDROIDX_MEDIA_VIEW_CLASS_NAME)
    )
    assertTrue(
      options.maskViewClasses.contains(SentryMaskingOptions.ANDROIDX_MEDIA_VIEW_CLASS_NAME)
    )
    assertTrue(options.maskViewClasses.contains(SentryMaskingOptions.EXOPLAYER_CLASS_NAME))
    assertTrue(options.maskViewClasses.contains(SentryMaskingOptions.EXOPLAYER_STYLED_CLASS_NAME))
  }

  @Test
  fun `setMaskAllImages false does not add sensitive view classes`() {
    val options = SentryScreenshotOptions()
    options.setMaskAllImages(false)

    assertFalse(options.maskViewClasses.contains(SentryMaskingOptions.WEB_VIEW_CLASS_NAME))
    assertFalse(options.maskViewClasses.contains(SentryMaskingOptions.VIDEO_VIEW_CLASS_NAME))
    assertTrue(options.unmaskViewClasses.contains(SentryMaskingOptions.IMAGE_VIEW_CLASS_NAME))
  }

  @Test
  fun `calling setMaskAllImages true multiple times does not duplicate classes`() {
    val options = SentryScreenshotOptions()
    options.setMaskAllImages(true)
    options.setMaskAllImages(true)
    options.setMaskAllImages(true)

    // CopyOnWriteArraySet should prevent duplicates
    assertEquals(7, options.maskViewClasses.size)
  }

  @Test
  fun `inherits addMaskViewClass from base class`() {
    val options = SentryScreenshotOptions()
    options.addMaskViewClass("com.example.CustomView")

    assertTrue(options.maskViewClasses.contains("com.example.CustomView"))
  }

  @Test
  fun `inherits addUnmaskViewClass from base class`() {
    val options = SentryScreenshotOptions()
    options.addUnmaskViewClass("com.example.SafeView")

    assertTrue(options.unmaskViewClasses.contains("com.example.SafeView"))
  }

  @Test
  fun `inherits container class methods from base class`() {
    val options = SentryScreenshotOptions()
    options.setMaskViewContainerClass("com.example.MaskContainer")
    options.setUnmaskViewContainerClass("com.example.UnmaskContainer")

    assertEquals("com.example.MaskContainer", options.maskViewContainerClass)
    assertEquals("com.example.UnmaskContainer", options.unmaskViewContainerClass)
  }

  @Test
  fun `setMaskAllImages false removes sensitive view classes added by true`() {
    val options = SentryScreenshotOptions()
    options.setMaskAllImages(true)

    // Verify classes were added
    assertTrue(options.maskViewClasses.contains(SentryMaskingOptions.WEB_VIEW_CLASS_NAME))
    assertTrue(options.maskViewClasses.contains(SentryMaskingOptions.VIDEO_VIEW_CLASS_NAME))

    options.setMaskAllImages(false)

    // Verify all sensitive classes were removed
    assertFalse(options.maskViewClasses.contains(SentryMaskingOptions.IMAGE_VIEW_CLASS_NAME))
    assertFalse(options.maskViewClasses.contains(SentryMaskingOptions.WEB_VIEW_CLASS_NAME))
    assertFalse(options.maskViewClasses.contains(SentryMaskingOptions.VIDEO_VIEW_CLASS_NAME))
    assertFalse(
      options.maskViewClasses.contains(SentryMaskingOptions.ANDROIDX_MEDIA_VIEW_CLASS_NAME)
    )
    assertFalse(options.maskViewClasses.contains(SentryMaskingOptions.EXOPLAYER_CLASS_NAME))
    assertFalse(options.maskViewClasses.contains(SentryMaskingOptions.EXOPLAYER_STYLED_CLASS_NAME))
    assertTrue(options.maskViewClasses.isEmpty())
  }
}
