package io.sentry.compose.navigation3.internal.backstack

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.SentryLevel
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class UnavailableBackStackEntryMapperTest {

  private data object Home

  private data class Detail(val id: String)

  private val logger = mock<ILogger>()

  @Test
  fun `maps every back stack entry to null`() {
    val mapper = UnavailableBackStackEntryMapper(logger = { logger })

    val mapped = listOf(Home, Detail("1"), "home").map(mapper::map)

    assertThat(mapped).containsExactly(null, null, null)
  }

  @Test
  fun `logs a warning once per back stack entry type`() {
    val mapper = UnavailableBackStackEntryMapper(logger = { logger })

    repeat(3) {
      mapper.map(Home)
      mapper.map(Detail("$it"))
    }

    verify(logger, times(1)).log(eq(SentryLevel.WARNING), any<String>(), eq(Home::class.java.name))
    verify(logger, times(1))
      .log(eq(SentryLevel.WARNING), any<String>(), eq(Detail::class.java.name))
  }
}
