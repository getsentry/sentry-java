package io.sentry.compose.navigation3

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.backstack.BuiltInBackStackEntryMappers
import org.junit.Test

class BackStackEntryMapperTest {

  private data object Home

  @Test
  fun `SerialName - returns the built-in serial name mapper as a mapper for any entry type`() {
    val homeMapper: BackStackEntryMapper<Home> = BackStackEntryMapper.SerialName

    assertThat(homeMapper).isSameInstanceAs(BuiltInBackStackEntryMappers.serialName)
  }
}
