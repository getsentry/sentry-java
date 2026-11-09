package io.sentry.asyncprofiler

import com.google.common.truth.Truth.assertThat
import io.sentry.ProfileLifecycle
import io.sentry.Sentry
import io.sentry.test.MockSentryServer
import io.sentry.test.initForTest
import io.sentry.test.profileChunk
import io.sentry.test.transaction
import kotlin.test.AfterTest
import kotlin.test.Test

/** Profiles a transaction with the async profiler and sends both over the real HTTP transport. */
class ContinuousProfilerEndToEndTest {
  private val server = MockSentryServer()

  @AfterTest
  fun teardown() {
    Sentry.close()
    server.close()
  }

  @Test
  fun `a transaction starts a profile whose chunk reaches Sentry`() {
    initForTest {
      it.dsn = server.dsn
      it.tracesSampleRate = 1.0
      it.profileSessionSampleRate = 1.0
      it.profileLifecycle = ProfileLifecycle.TRACE
      // The profiler rejects a traces path with a dot anywhere in it, which the module's absolute
      // path can have.
      it.profilingTracesDirPath =
        "${System.getProperty("java.io.tmpdir")}/sentry-profiler-end-to-end-test"
    }

    val transaction = Sentry.startTransaction("transaction name", "op")
    val span = transaction.startChild("child")
    Thread.sleep(100)
    span.finish()
    transaction.finish()

    val sent =
      server.awaitEnvelope { it.transaction()?.transaction == "transaction name" }.transaction()!!
    val profilerId = sent.contexts.profile?.profilerId
    assertThat(profilerId).isNotNull()
    server.awaitEnvelope(timeoutMillis = 30_000) { it.profileChunk()?.profilerId == profilerId }
  }
}
