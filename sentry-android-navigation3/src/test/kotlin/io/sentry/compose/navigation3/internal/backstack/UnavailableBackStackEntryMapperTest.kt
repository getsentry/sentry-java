package io.sentry.compose.navigation3.internal.backstack

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.SentryLevel
import io.sentry.compose.navigation3.internal.contract.UnknownEntryReason
import io.sentry.compose.navigation3.internal.contract.UnknownEntryReasonReporter
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

class UnavailableBackStackEntryMapperTest {

  private data object Home

  private data class Detail(val id: String)

  private val logger = mock<ILogger>()
  private val mapper = UnavailableBackStackEntryMapper(logger = { logger })

  @Test
  fun `map() - maps every back stack entry to null`() {
    val mapped = listOf(Home, Detail("1"), "home").map(mapper::map)

    assertThat(mapped).containsExactly(null, null, null)
  }

  @Test
  fun `map() - reports the kotlinx serialization version as the reason for every back stack entry`() {
    val reasons =
      listOf(Home, Detail("1"), Home).map { entry ->
        UnknownEntryReasonReporter.collect { mapper.map(entry) }.reason
      }

    assertThat(reasons)
      .containsExactly(
        UnknownEntryReason.KOTLINX_SERIALIZATION_VERSION,
        UnknownEntryReason.KOTLINX_SERIALIZATION_VERSION,
        UnknownEntryReason.KOTLINX_SERIALIZATION_VERSION,
      )
  }

  @Test
  fun `map() - logs a warning once per back stack entry type`() {
    repeat(3) {
      mapper.map(Home)
      mapper.map(Detail("$it"))
    }

    verify(logger, times(1)).log(eq(SentryLevel.WARNING), any<String>(), eq(Home::class.java.name))
    verify(logger, times(1))
      .log(eq(SentryLevel.WARNING), any<String>(), eq(Detail::class.java.name))
    verifyNoMoreInteractions(logger)
  }
}
