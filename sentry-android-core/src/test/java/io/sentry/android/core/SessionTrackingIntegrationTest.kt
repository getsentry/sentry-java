package io.sentry.android.core

import android.content.Context
import androidx.lifecycle.Lifecycle.Event.ON_START
import androidx.lifecycle.Lifecycle.Event.ON_STOP
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.CheckIn
import io.sentry.Hint
import io.sentry.IScope
import io.sentry.ISentryClient
import io.sentry.ProfileChunk
import io.sentry.ProfilingTraceData
import io.sentry.Sentry
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.SentryLogEvent
import io.sentry.SentryLogEvents
import io.sentry.SentryOptions
import io.sentry.SentryReplayEvent
import io.sentry.Session
import io.sentry.TraceContext
import io.sentry.UserFeedback
import io.sentry.protocol.Feedback
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryTransaction
import io.sentry.transport.RateLimiter
import java.util.LinkedList
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [31])
class SessionTrackingIntegrationTest {
  private lateinit var context: Context

  @BeforeTest
  fun `set up`() {
    context = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `session tracking works properly with multiple backgrounds and foregrounds`() {
    lateinit var options: SentryAndroidOptions
    initForTest(context) {
      it.dsn = "https://key@sentry.io/proj"
      it.release = "io.sentry.samples@2.3.0"
      it.environment = "production"
      it.sessionTrackingIntervalMillis = 0L
      options = it
    }
    val client = CapturingSentryClient()
    Sentry.bindClient(client)
    val lifecycle = setupLifecycle(options)
    val initSid = lastSessionId()

    lifecycle.handleLifecycleEvent(ON_START)
    val sidAfterFirstStart = lastSessionId()
    Thread.sleep(100L)
    lifecycle.handleLifecycleEvent(ON_STOP)
    Thread.sleep(100L)
    val sidAfterFirstStop = lastSessionId()

    Thread.sleep(100L)

    lifecycle.handleLifecycleEvent(ON_START)
    val sidAfterSecondStart = lastSessionId()
    Thread.sleep(100L)
    lifecycle.handleLifecycleEvent(ON_STOP)
    Thread.sleep(100L)
    val sidAfterSecondStop = lastSessionId()
    // we bind our CapturingSentryClient only after .init is called, so we'll be able to capture
    // only the Exited status of the session started in .init
    val initSessionUpdate = client.sessionUpdates.pop()
    assertTrue {
      initSid == initSessionUpdate.sessionId.toString() &&
        Session.State.Exited == initSessionUpdate.status
    }

    val afterFirstStartSessionUpdate = client.sessionUpdates.pop()
    assertTrue {
      sidAfterFirstStart == afterFirstStartSessionUpdate.sessionId.toString() &&
        Session.State.Ok == afterFirstStartSessionUpdate.status
    }

    val afterFirstStopSessionUpdate = client.sessionUpdates.pop()
    assertTrue {
      "null" == sidAfterFirstStop &&
        sidAfterFirstStart == afterFirstStopSessionUpdate.sessionId.toString() &&
        Session.State.Exited == afterFirstStopSessionUpdate.status
    }

    val afterSecondStartSessionUpdate = client.sessionUpdates.pop()
    assertTrue {
      sidAfterSecondStart == afterSecondStartSessionUpdate.sessionId.toString() &&
        Session.State.Ok == afterSecondStartSessionUpdate.status
    }

    val afterSecondStopSessionUpdate = client.sessionUpdates.pop()
    assertTrue {
      "null" == sidAfterSecondStop &&
        sidAfterSecondStart == afterSecondStopSessionUpdate.sessionId.toString() &&
        Session.State.Exited == afterSecondStopSessionUpdate.status
    }
  }

  private fun lastSessionId(): String? {
    var sid: String? = null
    Sentry.configureScope { scope -> sid = scope.session?.sessionId.toString() }
    return sid
  }

  private fun setupLifecycle(options: SentryOptions): LifecycleRegistry {
    val lifecycle = LifecycleRegistry(ProcessLifecycleOwner.get())
    val lifecycleWatcher =
      (options.integrations.find { it is AppLifecycleIntegration } as AppLifecycleIntegration)
        .watcher
    lifecycle.addObserver(lifecycleWatcher!!)
    return lifecycle
  }

  private class CapturingSentryClient : ISentryClient {
    val sessionUpdates = LinkedList<Session>()

    override fun isEnabled(): Boolean = true

    override fun captureEvent(event: SentryEvent, scope: IScope?, hint: Hint?): SentryId {
      TODO("Not yet implemented")
    }

    override fun close(isRestarting: Boolean) {
      TODO("Not yet implemented")
    }

    override fun close() {
      TODO("Not yet implemented")
    }

    override fun flush(timeoutMillis: Long) {
      TODO("Not yet implemented")
    }

    override fun captureFeedback(feedback: Feedback, hint: Hint?, scope: IScope): SentryId {
      TODO("Not yet implemented")
    }

    override fun captureReplayEvent(
      event: SentryReplayEvent,
      scope: IScope?,
      hint: Hint?,
    ): SentryId {
      TODO("Not yet implemented")
    }

    override fun captureUserFeedback(userFeedback: UserFeedback) {
      TODO("Not yet implemented")
    }

    override fun captureSession(session: Session, hint: Hint?) {
      sessionUpdates.add(session)
    }

    override fun captureEnvelope(envelope: SentryEnvelope, hint: Hint?): SentryId? {
      TODO("Not yet implemented")
    }

    override fun captureTransaction(
      transaction: SentryTransaction,
      traceContext: TraceContext?,
      scope: IScope?,
      hint: Hint?,
      profilingTraceData: ProfilingTraceData?,
    ): SentryId {
      TODO("Not yet implemented")
    }

    override fun captureProfileChunk(profileChunk: ProfileChunk, scope: IScope?): SentryId {
      TODO("Not yet implemented")
    }

    override fun captureCheckIn(checkIn: CheckIn, scope: IScope?, hint: Hint?): SentryId {
      TODO("Not yet implemented")
    }

    override fun captureLog(event: SentryLogEvent, scope: IScope?) {
      TODO("Not yet implemented")
    }

    override fun captureBatchedLogEvents(logEvents: SentryLogEvents) {
      TODO("Not yet implemented")
    }

    override fun getRateLimiter(): RateLimiter? {
      TODO("Not yet implemented")
    }
  }
}
