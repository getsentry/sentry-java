package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryMaskingOptionsTest {

  private class TestMaskingOptions : SentryMaskingOptions()

  @Test
  fun `maskViewClasses is empty by default`() {
    val options = TestMaskingOptions()
    assertTrue(options.maskViewClasses.isEmpty())
  }

  @Test
  fun `unmaskViewClasses is empty by default`() {
    val options = TestMaskingOptions()
    assertTrue(options.unmaskViewClasses.isEmpty())
  }

  @Test
  fun `addMaskViewClass adds class to set`() {
    val options = TestMaskingOptions()
    options.addMaskViewClass("com.example.MyView")
    assertTrue(options.maskViewClasses.contains("com.example.MyView"))
  }

  @Test
  fun `addUnmaskViewClass adds class to set`() {
    val options = TestMaskingOptions()
    options.addUnmaskViewClass("com.example.MyView")
    assertTrue(options.unmaskViewClasses.contains("com.example.MyView"))
  }

  @Test
  fun `setMaskAllText true adds TextView to maskViewClasses`() {
    val options = TestMaskingOptions()
    options.setMaskAllText(true)
    assertTrue(options.maskViewClasses.contains(SentryMaskingOptions.TEXT_VIEW_CLASS_NAME))
    assertFalse(options.unmaskViewClasses.contains(SentryMaskingOptions.TEXT_VIEW_CLASS_NAME))
  }

  @Test
  fun `setMaskAllText false adds TextView to unmaskViewClasses`() {
    val options = TestMaskingOptions()
    options.setMaskAllText(false)
    assertTrue(options.unmaskViewClasses.contains(SentryMaskingOptions.TEXT_VIEW_CLASS_NAME))
    assertFalse(options.maskViewClasses.contains(SentryMaskingOptions.TEXT_VIEW_CLASS_NAME))
  }

  @Test
  fun `setMaskAllText true removes TextView from unmaskViewClasses`() {
    val options = TestMaskingOptions()
    options.addUnmaskViewClass(SentryMaskingOptions.TEXT_VIEW_CLASS_NAME)
    options.setMaskAllText(true)
    assertFalse(options.unmaskViewClasses.contains(SentryMaskingOptions.TEXT_VIEW_CLASS_NAME))
  }

  @Test
  fun `setMaskAllText false removes TextView from maskViewClasses`() {
    val options = TestMaskingOptions()
    options.addMaskViewClass(SentryMaskingOptions.TEXT_VIEW_CLASS_NAME)
    options.setMaskAllText(false)
    assertFalse(options.maskViewClasses.contains(SentryMaskingOptions.TEXT_VIEW_CLASS_NAME))
  }

  @Test
  fun `setMaskAllImages true adds ImageView to maskViewClasses`() {
    val options = TestMaskingOptions()
    options.setMaskAllImages(true)
    assertTrue(options.maskViewClasses.contains(SentryMaskingOptions.IMAGE_VIEW_CLASS_NAME))
    assertFalse(options.unmaskViewClasses.contains(SentryMaskingOptions.IMAGE_VIEW_CLASS_NAME))
  }

  @Test
  fun `setMaskAllImages false adds ImageView to unmaskViewClasses`() {
    val options = TestMaskingOptions()
    options.setMaskAllImages(false)
    assertTrue(options.unmaskViewClasses.contains(SentryMaskingOptions.IMAGE_VIEW_CLASS_NAME))
    assertFalse(options.maskViewClasses.contains(SentryMaskingOptions.IMAGE_VIEW_CLASS_NAME))
  }

  @Test
  fun `setMaskAllImages true removes ImageView from unmaskViewClasses`() {
    val options = TestMaskingOptions()
    options.addUnmaskViewClass(SentryMaskingOptions.IMAGE_VIEW_CLASS_NAME)
    options.setMaskAllImages(true)
    assertFalse(options.unmaskViewClasses.contains(SentryMaskingOptions.IMAGE_VIEW_CLASS_NAME))
  }

  @Test
  fun `setMaskAllImages false removes ImageView from maskViewClasses`() {
    val options = TestMaskingOptions()
    options.addMaskViewClass(SentryMaskingOptions.IMAGE_VIEW_CLASS_NAME)
    options.setMaskAllImages(false)
    assertFalse(options.maskViewClasses.contains(SentryMaskingOptions.IMAGE_VIEW_CLASS_NAME))
  }

  @Test
  fun `maskViewContainerClass is null by default`() {
    val options = TestMaskingOptions()
    assertNull(options.maskViewContainerClass)
  }

  @Test
  fun `unmaskViewContainerClass is null by default`() {
    val options = TestMaskingOptions()
    assertNull(options.unmaskViewContainerClass)
  }

  @Test
  fun `setMaskViewContainerClass sets container and adds to maskViewClasses`() {
    val options = TestMaskingOptions()
    options.setMaskViewContainerClass("com.example.Container")
    assertEquals("com.example.Container", options.maskViewContainerClass)
    assertTrue(options.maskViewClasses.contains("com.example.Container"))
  }

  @Test
  fun `setUnmaskViewContainerClass sets container`() {
    val options = TestMaskingOptions()
    options.setUnmaskViewContainerClass("com.example.Container")
    assertEquals("com.example.Container", options.unmaskViewContainerClass)
  }
}
