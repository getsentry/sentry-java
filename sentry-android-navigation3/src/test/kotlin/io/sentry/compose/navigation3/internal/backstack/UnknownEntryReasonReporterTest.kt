package io.sentry.compose.navigation3.internal.backstack

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.contract.UnknownEntryReason
import kotlin.concurrent.thread
import kotlin.test.assertFailsWith
import org.junit.Test

class UnknownEntryReasonReporterTest {

  @Test
  fun `collect() - returns the block's result without a reason when nothing is reported`() {
    val collected = collectWithReason { "result" }

    assertThat(collected).isEqualTo("result" to null)
  }

  @Test
  fun `collect() - returns the block's result with the reason reported while it ran`() {
    val collected = collectWithReason {
      UnknownEntryReasonReporter.report(UnknownEntryReason.ENTRY_NOT_ANNOTATED)
      null
    }

    assertThat(collected).isEqualTo(null to UnknownEntryReason.ENTRY_NOT_ANNOTATED)
  }

  @Test
  fun `collect() - returns the last reason reported when the block reports several`() {
    val collected = collectWithReason {
      UnknownEntryReasonReporter.report(UnknownEntryReason.ENTRY_NOT_ANNOTATED)
      UnknownEntryReasonReporter.report(UnknownEntryReason.GENERIC_ENTRY_TYPE)
      null
    }

    assertThat(collected.second).isEqualTo(UnknownEntryReason.GENERIC_ENTRY_TYPE)
  }

  @Test
  fun `collect() - ignores a reason reported before it was called`() {
    UnknownEntryReasonReporter.report(UnknownEntryReason.ENTRY_NOT_ANNOTATED)

    val collected = collectWithReason { null }

    assertThat(collected.second).isNull()
  }

  @Test
  fun `collect() - does not keep the reason reported in its block after it returns`() {
    val collected = collectWithReason {
      collectWithReason {
        UnknownEntryReasonReporter.report(UnknownEntryReason.ENTRY_NOT_ANNOTATED)
        null
      }
      null
    }

    assertThat(collected.second).isNull()
  }

  @Test
  fun `collect() - does not keep the reason reported in its block after the block throws`() {
    val collected = collectWithReason {
      runCatching {
        collectWithReason {
          UnknownEntryReasonReporter.report(UnknownEntryReason.ENTRY_NOT_ANNOTATED)
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
      assertFailsWith<IllegalStateException> {
        collectWithReason { throw exception }
      }

    assertThat(thrown).isSameInstanceAs(exception)
  }

  @Test
  fun `collect() - does not pass a reason to onCollected when the block throws`() {
    var collected = false

    runCatching {
      UnknownEntryReasonReporter.collect(
        map = {
          UnknownEntryReasonReporter.report(UnknownEntryReason.ENTRY_NOT_ANNOTATED)
          error("boom")
        },
        onCollected = { collected = true },
      )
    }

    assertThat(collected).isFalse()
  }

  @Test
  fun `collect() - ignores a reason reported on another thread while the block runs`() {
    val collected = collectWithReason {
      thread { UnknownEntryReasonReporter.report(UnknownEntryReason.ENTRY_NOT_ANNOTATED) }.join()
      null
    }

    assertThat(collected.second).isNull()
  }
}
