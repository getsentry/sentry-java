package io.sentry

import com.google.common.truth.Truth.assertThat
import io.sentry.protocol.SentryId
import io.sentry.test.createTestScopes
import kotlin.test.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DefaultSpanFactoryTest {
  private val continuousProfiler = mock<IContinuousProfiler>()
  private val profilerId = SentryId()

  private fun createTransaction(sampled: Boolean): ITransaction {
    val options = SentryOptions().apply { dsn = "https://key@sentry.io/proj" }
    // createTestScopes runs init, which resets the profiler, so it has to be set afterwards
    val scopes = createTestScopes(options)
    options.setContinuousProfiler(continuousProfiler)

    return DefaultSpanFactory()
      .createTransaction(
        TransactionContext("name", "op", TracesSamplingDecision(sampled)),
        scopes,
        TransactionOptions(),
        null,
      )
  }

  @Test
  fun `registers the created transaction for profiling cancellation`() {
    whenever(continuousProfiler.profilerId).thenReturn(profilerId)

    val transaction = createTransaction(sampled = true)

    verify(continuousProfiler).registerProfilingCanceledCallback(transaction as SentryTracer)
  }

  @Test
  fun `the registered transaction drops its profiler id when profiling is canceled`() {
    whenever(continuousProfiler.profilerId).thenReturn(profilerId)
    val transaction = createTransaction(sampled = true)
    assertThat(transaction.contexts.profile).isNotNull()

    (transaction as IProfilingCanceledCallback).onProfilingCanceled(profilerId)

    assertThat(transaction.contexts.profile).isNull()
  }

  @Test
  fun `does not register an unsampled transaction`() {
    whenever(continuousProfiler.profilerId).thenReturn(profilerId)

    createTransaction(sampled = false)

    verify(continuousProfiler, never()).registerProfilingCanceledCallback(any())
  }

  @Test
  fun `does not register when the profiler is not running`() {
    whenever(continuousProfiler.profilerId).thenReturn(SentryId.EMPTY_ID)

    createTransaction(sampled = true)

    verify(continuousProfiler, never()).registerProfilingCanceledCallback(any())
  }
}
