package io.sentry.samples.android.navigation

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlin.test.assertFailsWith

class NavigationPerformanceControlsTest {

  @Test
  fun `duration percentiles use nearest rank`() {
    val durations = NavigationPerformanceDurations()
    listOf(1L, 2L, 3L, 4L, 100L).forEach(durations::add)

    assertThat(durations.percentile(50)).isEqualTo(3L)
    assertThat(durations.percentile(90)).isEqualTo(100L)
    assertThat(durations.percentile(100)).isEqualTo(100L)
  }

  @Test
  fun `duration count uses strict frame threshold`() {
    val durations = NavigationPerformanceDurations()
    listOf(8_299_999L, 8_300_000L, 8_300_001L).forEach(durations::add)

    assertThat(durations.countOver(8_300_000L)).isEqualTo(1)
  }

  @Test
  fun `subtracting extractor time never produces negative duration`() {
    val effectDurations = NavigationPerformanceDurations().apply { add(10L) }
    val extractorDurations = NavigationPerformanceDurations().apply { add(12L) }

    assertThat(effectDurations.minus(extractorDurations).percentile(50)).isEqualTo(0L)
  }

  @Test
  fun `invalid percentile is rejected`() {
    assertFailsWith<IllegalArgumentException> {
      NavigationPerformanceDurations().percentile(101)
    }
  }

  @Test
  fun `workload ladder increases captured depth and argument complexity`() {
    val ladder =
      listOf(
        NavigationPerformancePreset.LIGHT,
        NavigationPerformancePreset.NORMAL,
        NavigationPerformancePreset.HEAVY,
        NavigationPerformancePreset.SUPER_HEAVY,
      )

    assertThat(ladder.map { it.stackDepth }).containsExactly(1, 20, 50, 100).inOrder()
    assertThat(ladder.map { it.maxCapturedBackStackEntries })
      .containsExactly(1, 20, 50, 100)
      .inOrder()
    assertThat(ladder.map { it.argumentMode })
      .containsExactly(
        NavigationPerformanceArgumentMode.EMPTY,
        NavigationPerformanceArgumentMode.FLAT,
        NavigationPerformanceArgumentMode.NESTED,
        NavigationPerformanceArgumentMode.LARGE,
      )
      .inOrder()
    assertThat(ladder.map { it.integrationMode }.distinct())
      .containsExactly(NavigationPerformanceIntegrationMode.FULL_STACK)
    assertThat(ladder.map { it.extractorMode })
      .containsExactly(
        NavigationPerformanceExtractorMode.NORMAL,
        NavigationPerformanceExtractorMode.NORMAL,
        NavigationPerformanceExtractorMode.NORMAL,
        NavigationPerformanceExtractorMode.HEAVY,
      )
      .inOrder()
  }

  @Test
  fun `no arguments mode captures stack without argument extraction`() {
    val mode = NavigationPerformanceIntegrationMode.FULL_STACK_WITHOUT_ARGUMENTS

    assertThat(mode.captureBackStack).isTrue()
    assertThat(mode.includeArguments).isFalse()
  }

  @Test
  fun `no arguments preset matches super heavy stack without argument extraction`() {
    val preset = NavigationPerformancePreset.NO_ARGUMENTS

    assertThat(preset.stackDepth).isEqualTo(NavigationPerformancePreset.SUPER_HEAVY.stackDepth)
    assertThat(preset.maxCapturedBackStackEntries)
      .isEqualTo(NavigationPerformancePreset.SUPER_HEAVY.maxCapturedBackStackEntries)
    assertThat(preset.extractorMode)
      .isEqualTo(NavigationPerformancePreset.SUPER_HEAVY.extractorMode)
    assertThat(preset.integrationMode)
      .isEqualTo(NavigationPerformanceIntegrationMode.FULL_STACK_WITHOUT_ARGUMENTS)
  }

  @Test
  fun `capture scaling presets keep inputs fixed while varying effective capture depth`() {
    val presets =
      listOf(
        NavigationPerformancePreset.NORMAL,
        NavigationPerformancePreset.CAPTURE_1_OF_100,
        NavigationPerformancePreset.CAPTURE_20_OF_100,
        NavigationPerformancePreset.CAPTURE_100_OF_100,
      )

    assertThat(presets.map { it.integrationMode }.distinct())
      .containsExactly(NavigationPerformanceIntegrationMode.FULL_STACK)
    assertThat(presets.map { it.extractorMode }.distinct())
      .containsExactly(NavigationPerformanceExtractorMode.NORMAL)
    assertThat(presets.map { it.argumentMode }.distinct())
      .containsExactly(NavigationPerformanceArgumentMode.FLAT)
    assertThat(presets.map { it.stackDepth }).containsExactly(20, 100, 100, 100).inOrder()
    assertThat(presets.map { it.maxCapturedBackStackEntries })
      .containsExactly(20, 1, 20, 100)
      .inOrder()
  }
}
