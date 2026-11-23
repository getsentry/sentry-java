package io.sentry.android.core.hang

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class RuntimeStatsCollectorTest {

  @Test
  fun `sample returns a read of the source`() {
    val snapshot = RuntimeStatsSnapshot()
    val collector = RuntimeStatsCollector { snapshot }

    assertThat(collector.sample()).isSameInstanceAs(snapshot)
  }

  @Test
  fun `each sample reads the source again`() {
    var reads = 0
    val collector = RuntimeStatsCollector {
      reads++
      RuntimeStatsSnapshot()
    }

    collector.sample()
    collector.sample()

    assertThat(reads).isEqualTo(2)
  }
}
