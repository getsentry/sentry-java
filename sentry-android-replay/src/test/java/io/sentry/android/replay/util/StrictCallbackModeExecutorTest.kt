package io.sentry.android.replay.util

import com.google.common.truth.Truth.assertThat
import io.sentry.SentryExecutorService
import io.sentry.SentryOptions
import io.sentry.exception.SentryCallbackError
import io.sentry.exception.SentryCallbackException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFailsWith

class StrictCallbackModeExecutorTest {
  @Test
  fun `replay executor preserves callback failures in task results`() {
    val executor =
      ReplayExecutorService(Executors.newSingleThreadScheduledExecutor(), SentryOptions())
    try {
      for (marker in
        listOf(
          SentryCallbackException(IllegalStateException()),
          SentryCallbackError(LinkageError()),
        )) {
        val future = executor.submit(Runnable { throw marker })!!
        val thrown = assertFailsWith<ExecutionException> { future.get(5, TimeUnit.SECONDS) }
        assertThat(thrown.cause).isSameInstanceAs(marker)
      }
    } finally {
      executor.shutdownNow()
    }
  }

  @Test
  fun `safe submission preserves callback failures in task results`() {
    val options = SentryOptions()
    val executor = SentryExecutorService(options)
    try {
      for (marker in
        listOf(
          SentryCallbackException(IllegalStateException()),
          SentryCallbackError(LinkageError()),
        )) {
        val future =
          executor.submitSafely(options, "strict-callback-mode-test", Runnable { throw marker })!!
        val thrown = assertFailsWith<ExecutionException> { future.get(5, TimeUnit.SECONDS) }
        assertThat(thrown.cause).isSameInstanceAs(marker)
      }
    } finally {
      executor.close(1000)
    }
  }
}
