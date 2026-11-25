package io.sentry.compose.navigation3.internal.backstack

import com.google.common.truth.Truth.assertThat
import kotlin.concurrent.thread
import kotlin.test.assertFailsWith
import org.junit.Test

class UnnamedReasonReportsTest {

  @Test
  fun `collect() - returns the block's result without a reason when nothing is reported`() {
    val collected = UnnamedReasonReports.collect { "result" }

    assertThat(collected).isEqualTo("result" to null)
  }

  @Test
  fun `collect() - returns the block's result with the reason reported while it ran`() {
    val collected = UnnamedReasonReports.collect {
      UnnamedReasonReports.report(UnnamedReason.NOT_SERIALIZABLE)
      null
    }

    assertThat(collected).isEqualTo(null to UnnamedReason.NOT_SERIALIZABLE)
  }

  @Test
  fun `collect() - returns the last reason reported when the block reports several`() {
    val collected = UnnamedReasonReports.collect {
      UnnamedReasonReports.report(UnnamedReason.NOT_SERIALIZABLE)
      UnnamedReasonReports.report(UnnamedReason.GENERIC_TYPE)
      null
    }

    assertThat(collected.second).isEqualTo(UnnamedReason.GENERIC_TYPE)
  }

  @Test
  fun `collect() - ignores a reason reported before it was called`() {
    UnnamedReasonReports.report(UnnamedReason.NOT_SERIALIZABLE)

    val collected = UnnamedReasonReports.collect { null }

    assertThat(collected.second).isNull()
  }

  @Test
  fun `collect() - does not keep the reason reported in its block after it returns`() {
    val collected = UnnamedReasonReports.collect {
      UnnamedReasonReports.collect {
        UnnamedReasonReports.report(UnnamedReason.NOT_SERIALIZABLE)
        null
      }
      null
    }

    assertThat(collected.second).isNull()
  }

  @Test
  fun `collect() - does not keep the reason reported in its block after the block throws`() {
    val collected = UnnamedReasonReports.collect {
      runCatching {
        UnnamedReasonReports.collect {
          UnnamedReasonReports.report(UnnamedReason.NOT_SERIALIZABLE)
          error("boom")
        }
      }
      null
    }

    assertThat(collected.second).isNull()
  }

  @Test
  fun `collect() - rethrows the exception thrown by the block unchanged`() {
    val exception = IllegalStateException("boom")

    val thrown =
      assertFailsWith<IllegalStateException> { UnnamedReasonReports.collect { throw exception } }

    assertThat(thrown).isSameInstanceAs(exception)
  }

  @Test
  fun `collect() - ignores a reason reported on another thread while the block runs`() {
    val collected = UnnamedReasonReports.collect {
      thread { UnnamedReasonReports.report(UnnamedReason.NOT_SERIALIZABLE) }.join()
      null
    }

    assertThat(collected.second).isNull()
  }
}
