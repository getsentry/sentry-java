package io.sentry.android.core

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.SentryLevel
import kotlin.test.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.N])
class AndroidRuntimeStatsSourceTest {

  private val logger = mock<ILogger>()

  private fun source(values: Map<String, String?>) =
    AndroidRuntimeStatsSource(logger, AndroidRuntimeStatsSource.StatReader { values[it] })

  @Test
  fun `reads gc counters and times`() {
    val snapshot =
      source(
          mapOf(
            AndroidRuntimeStatsSource.GC_COUNT to "12",
            AndroidRuntimeStatsSource.GC_TIME to "340",
            AndroidRuntimeStatsSource.BLOCKING_GC_COUNT to "3",
            AndroidRuntimeStatsSource.BLOCKING_GC_TIME to "55",
            AndroidRuntimeStatsSource.PRE_OOME_GC_COUNT to "1",
          ),
        )
        .read()

    assertThat(snapshot.gcCount).isEqualTo(12L)
    assertThat(snapshot.gcTimeMs).isEqualTo(340.0)
    assertThat(snapshot.blockingGcCount).isEqualTo(3L)
    assertThat(snapshot.blockingGcTimeMs).isEqualTo(55.0)
    assertThat(snapshot.preOomeGcCount).isEqualTo(1L)
  }

  @Test
  fun `converts the waiting time from nanoseconds to milliseconds`() {
    val snapshot = source(mapOf(AndroidRuntimeStatsSource.GC_WAITING_TIME to "2500000")).read()

    assertThat(snapshot.gcWaitingTimeMs).isEqualTo(2.5)
  }

  @Test
  fun `missing and malformed keys are null`() {
    val snapshot = source(mapOf(AndroidRuntimeStatsSource.GC_COUNT to "abc")).read()

    assertThat(snapshot.gcCount).isNull()
    assertThat(snapshot.gcTimeMs).isNull()
    assertThat(snapshot.gcWaitingTimeMs).isNull()
  }

  @Test
  fun `a malformed key is logged`() {
    source(mapOf(AndroidRuntimeStatsSource.GC_COUNT to "abc")).read()

    verify(logger)
      .log(
        eq(SentryLevel.WARNING),
        any<NumberFormatException>(),
        eq("Could not parse the runtime stat %s: %s"),
        eq(AndroidRuntimeStatsSource.GC_COUNT),
        eq("abc"),
      )
  }

  @Test
  fun `memory comes from the runtime and free until oome is the heap headroom`() {
    val runtime = Runtime.getRuntime()

    val snapshot = source(emptyMap()).read()

    assertThat(snapshot.maxMemory).isEqualTo(runtime.maxMemory())
    assertThat(snapshot.totalMemory).isGreaterThan(0L)
    assertThat(snapshot.freeMemoryUntilOome)
      .isEqualTo(snapshot.maxMemory!! - (snapshot.totalMemory!! - snapshot.freeMemory!!))
  }
}
