package io.sentry.android.buddy

import com.google.common.truth.Truth.assertThat
import io.sentry.android.buddy.model.BuddyExceptionFrame
import kotlin.test.Test

class BuddySourcePathResolverTest {
  private fun frame(module: String?, filename: String?) =
    BuddyExceptionFrame(
      function = "onCreate",
      module = module,
      filename = filename,
      lineno = 42,
      inApp = true,
    )

  @Test
  fun `builds a source path from base path package and filename`() {
    val path =
      BuddySourcePathResolver.resolve(
        "/repo/app",
        frame("io.sentry.samples.android.MainActivity", "MainActivity.kt"),
      )

    assertThat(path).isEqualTo("/repo/app/src/main/java/io/sentry/samples/android/MainActivity.kt")
  }

  @Test
  fun `trims a trailing slash on the base path`() {
    val path = BuddySourcePathResolver.resolve("/repo/app/", frame("com.a.B", "B.kt"))

    assertThat(path).isEqualTo("/repo/app/src/main/java/com/a/B.kt")
  }

  @Test
  fun `returns null without a base path`() {
    assertThat(BuddySourcePathResolver.resolve(null, frame("com.a.B", "B.kt"))).isNull()
    assertThat(BuddySourcePathResolver.resolve("  ", frame("com.a.B", "B.kt"))).isNull()
  }

  @Test
  fun `returns null without a filename`() {
    assertThat(BuddySourcePathResolver.resolve("/repo/app", frame("com.a.B", null))).isNull()
  }
}
