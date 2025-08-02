package io.sentry.util

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class PatternUtilsTest {

  @Test
  fun `matchesPattern returns true for exact match`() {
    assertTrue(PatternUtils.matchesPattern("com.example.app", "com.example.app"))
  }

  @Test
  fun `matchesPattern returns false for non-matching strings`() {
    assertFalse(PatternUtils.matchesPattern("com.example.app", "com.other.app"))
  }

  @Test
  fun `matchesPattern returns true for suffix wildcard matching prefix`() {
    assertTrue(PatternUtils.matchesPattern("com.example.app", "com.example.*"))
    assertTrue(PatternUtils.matchesPattern("com.example.app.MainActivity", "com.example.*"))
    assertTrue(PatternUtils.matchesPattern("com.example.SomeClass", "com.example.*"))
  }

  @Test
  fun `matchesPattern returns false for suffix wildcard not matching prefix`() {
    assertFalse(PatternUtils.matchesPattern("com.other.app", "com.example.*"))
    assertFalse(PatternUtils.matchesPattern("org.example.app", "com.example.*"))
    // Exact package name should not match suffix wildcard
    assertFalse(PatternUtils.matchesPattern("com.example", "com.example.*"))
  }

  @Test
  fun `matchesPattern returns true for empty prefix with suffix wildcard`() {
    assertTrue(PatternUtils.matchesPattern("anything", "*"))
    assertTrue(PatternUtils.matchesPattern("", "*"))
    assertTrue(PatternUtils.matchesPattern("com.example.app", "*"))
  }

  @Test
  fun `matchesPattern returns false for wildcards in middle or beginning`() {
    // Wildcards in the middle are not supported
    assertFalse(PatternUtils.matchesPattern("com.example.pdf.viewer", "*pdf*"))
    assertFalse(PatternUtils.matchesPattern("com.example.pdf.viewer", "com.*.pdf"))
    assertFalse(PatternUtils.matchesPattern("com.example.pdf.viewer", "com.*pdf*"))

    // Wildcards at the beginning (not at the end) are not supported
    assertFalse(PatternUtils.matchesPattern("com.example.app", "*example"))
    assertFalse(PatternUtils.matchesPattern("com.example.app", "*example.app"))
  }

  @Test
  fun `matchesPattern handles complex package names with suffix wildcards`() {
    assertTrue(
      PatternUtils.matchesPattern("com.thirdparty.pdf.renderer.PdfView", "com.thirdparty.*")
    )
    assertTrue(
      PatternUtils.matchesPattern("com.thirdparty.trusted.TrustedView", "com.thirdparty.*")
    )
    assertTrue(
      PatternUtils.matchesPattern("com.thirdparty.pdf.ExactPdfView", "com.thirdparty.pdf.*")
    )

    assertFalse(PatternUtils.matchesPattern("com.thirdparty.pdf.renderer.PdfView", "com.other.*"))
    assertFalse(PatternUtils.matchesPattern("org.thirdparty.SomeView", "com.thirdparty.*"))
  }

  @Test
  fun `matchesAnyPattern returns true when input matches any pattern`() {
    val patterns = setOf("com.example.*", "com.thirdparty.*", "org.test.ExactClass")

    assertTrue(PatternUtils.matchesAnyPattern("com.example.app", patterns))
    assertTrue(PatternUtils.matchesAnyPattern("com.thirdparty.pdf.PdfView", patterns))
    assertTrue(PatternUtils.matchesAnyPattern("org.test.ExactClass", patterns))
  }

  @Test
  fun `matchesAnyPattern returns false when input matches no patterns`() {
    val patterns = setOf("com.example.*", "com.thirdparty.*", "org.test.ExactClass")

    assertFalse(PatternUtils.matchesAnyPattern("com.other.app", patterns))
    assertFalse(PatternUtils.matchesAnyPattern("org.test.OtherClass", patterns))
    assertFalse(PatternUtils.matchesAnyPattern("net.example.app", patterns))
  }

  @Test
  fun `matchesAnyPattern returns false for empty patterns`() {
    val patterns = emptySet<String>()

    assertFalse(PatternUtils.matchesAnyPattern("com.example.app", patterns))
  }
}
