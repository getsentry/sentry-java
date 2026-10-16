package io.sentry.compose.navigation3

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.SentryNavAutoInstrumentation.BackStackMappingType
import org.junit.Test

class SentryNavAutoInstrumentationTest {

  @Test
  fun `DEFAULT resolves to serialName`() {
    assertThat(BackStackMappingType.DEFAULT.toBackStackEntryMapper())
      .isSameInstanceAs(BackStackEntryMapper.serialName())
  }

  @Test
  fun `NAMES_ONLY resolves to serialName`() {
    assertThat(BackStackMappingType.NAMES_ONLY.toBackStackEntryMapper())
      .isSameInstanceAs(BackStackEntryMapper.serialName())
  }

  @Test
  fun `NAMES_AND_ARGUMENTS resolves to serialNameWithArguments`() {
    assertThat(BackStackMappingType.NAMES_AND_ARGUMENTS.toBackStackEntryMapper())
      .isSameInstanceAs(BackStackEntryMapper.serialNameWithArguments())
  }
}
