package io.sentry.android.core.anr

import com.google.common.truth.Truth.assertThat
import io.sentry.SentryOptions
import java.io.File
import kotlin.test.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class NonFatalAnrMarkerTest {

  @get:Rule val tmpDir = TemporaryFolder()

  private val options by lazy { SentryOptions().apply { cacheDirPath = tmpDir.root.absolutePath } }
  private val file by lazy { File(tmpDir.root, "last_non_fatal_anr") }

  @Test
  fun `read returns null without a marker`() {
    assertThat(NonFatalAnrMarker.read(options)).isNull()
  }

  @Test
  fun `read returns what was written`() {
    NonFatalAnrMarker.write(options, 42, 1_000, 2_500)

    val marker = NonFatalAnrMarker.read(options)!!

    assertThat(marker.matches(42, 1_000)).isTrue()
    assertThat(marker.matches(42, 2_500)).isTrue()
    assertThat(marker.matches(43, 2_000)).isFalse()
  }

  @Test
  fun `write overwrites the previous marker`() {
    NonFatalAnrMarker.write(options, 1, 1_000, 2_000)
    NonFatalAnrMarker.write(options, 2, 5_000, 6_000)

    val marker = NonFatalAnrMarker.read(options)!!

    assertThat(marker.matches(2, 5_500)).isTrue()
    assertThat(marker.matches(1, 1_500)).isFalse()
  }

  @Test
  fun `matches covers the window until 30 seconds after the end`() {
    NonFatalAnrMarker.write(options, 7, 10_000, 12_000)
    val marker = NonFatalAnrMarker.read(options)!!

    assertThat(marker.matches(7, 9_999)).isFalse()
    assertThat(marker.matches(7, 10_000)).isTrue()
    assertThat(marker.matches(7, 42_000)).isTrue()
    assertThat(marker.matches(7, 42_001)).isFalse()
  }

  @Test
  fun `read returns null for a corrupt file`() {
    file.writeBytes(byteArrayOf(0, 1, 2))

    assertThat(NonFatalAnrMarker.read(options)).isNull()
  }

  @Test
  fun `read returns null for an unknown version`() {
    NonFatalAnrMarker.write(options, 1, 1_000, 2_000)
    val bytes = file.readBytes()
    bytes[1] = 99
    file.writeBytes(bytes)

    assertThat(NonFatalAnrMarker.read(options)).isNull()
  }

  @Test
  fun `delete removes the marker`() {
    NonFatalAnrMarker.write(options, 1, 1_000, 2_000)

    NonFatalAnrMarker.delete(options)

    assertThat(file.exists()).isFalse()
    assertThat(NonFatalAnrMarker.read(options)).isNull()
  }

  @Test
  fun `write and read do nothing without a cache dir`() {
    options.cacheDirPath = null

    NonFatalAnrMarker.write(options, 1, 1_000, 2_000)

    assertThat(NonFatalAnrMarker.read(options)).isNull()
    NonFatalAnrMarker.delete(options)
  }
}
