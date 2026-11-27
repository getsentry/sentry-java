package io.sentry.compose.navigation3.internal.contract

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.contract.UnknownEntryReasonReporter.Collected
import kotlin.concurrent.thread
import kotlin.test.assertFailsWith
import org.junit.Test

class UnknownEntryReasonReporterTest {

  @Test
  fun `collect() - returns the block's result without a reason when nothing is reported`() {
    val collected = UnknownEntryReasonReporter.collect { "result" }

    assertThat(collected).isEqualTo(Collected("result", null))
  }

  @Test
  fun `collect() - returns the block's result with the reason reported while it ran`() {
    val collected = UnknownEntryReasonReporter.collect {
      UnknownEntryReasonReporter.report(UnknownEntryReason.ENTRY_NOT_ANNOTATED)
      null
    }

    assertThat(collected).isEqualTo(Collected(null, UnknownEntryReason.ENTRY_NOT_ANNOTATED))
  }

  @Test
  fun `collect() - returns the last reason reported when the block reports several`() {
    val collected = UnknownEntryReasonReporter.collect {
      UnknownEntryReasonReporter.report(UnknownEntryReason.ENTRY_NOT_ANNOTATED)
      UnknownEntryReasonReporter.report(UnknownEntryReason.GENERIC_ENTRY_TYPE)
      null
    }

    assertThat(collected.reason).isEqualTo(UnknownEntryReason.GENERIC_ENTRY_TYPE)
  }

  @Test
  fun `collect() - ignores a reason reported before it was called`() {
    UnknownEntryReasonReporter.report(UnknownEntryReason.ENTRY_NOT_ANNOTATED)

    val collected = UnknownEntryReasonReporter.collect { null }

    assertThat(collected.reason).isNull()
  }

  @Test
  fun `collect() - does not keep the reason reported in its block after it returns`() {
    val collected = UnknownEntryReasonReporter.collect {
      UnknownEntryReasonReporter.collect {
        UnknownEntryReasonReporter.report(UnknownEntryReason.ENTRY_NOT_ANNOTATED)
        null
      }
      null
    }

    assertThat(collected.reason).isNull()
  }

  @Test
  fun `collect() - does not keep the reason reported in its block after the block throws`() {
    val collected = UnknownEntryReasonReporter.collect {
      runCatching {
        UnknownEntryReasonReporter.collect {
          UnknownEntryReasonReporter.report(UnknownEntryReason.ENTRY_NOT_ANNOTATED)
          error("boom")
        }
      }
      null
    }

    assertThat(collected.reason).isNull()
  }

  @Test
  fun `collect() - rethrows the exception thrown by the block unchanged`() {
    val exception = IllegalStateException("boom")

    val thrown =
      assertFailsWith<IllegalStateException> {
        UnknownEntryReasonReporter.collect { throw exception }
      }

    assertThat(thrown).isSameInstanceAs(exception)
  }

  @Test
  fun `collect() - ignores a reason reported on another thread while the block runs`() {
    val collected = UnknownEntryReasonReporter.collect {
      thread { UnknownEntryReasonReporter.report(UnknownEntryReason.ENTRY_NOT_ANNOTATED) }.join()
      null
    }

    assertThat(collected.reason).isNull()
  }
}
