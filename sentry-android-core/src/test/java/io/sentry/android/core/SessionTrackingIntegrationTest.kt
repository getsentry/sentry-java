package io.sentry.android.core

import android.content.Context
import androidx.lifecycle.Lifecycle.Event.ON_START
import androidx.lifecycle.Lifecycle.Event.ON_STOP
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Hint
import io.sentry.ISentryClient
import io.sentry.ProfilingTraceData
import io.sentry.Scope
import io.sentry.Sentry
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.Session
import io.sentry.Session.State.Exited
import io.sentry.Session.State.Ok
import io.sentry.TraceContext
import io.sentry.UserFeedback
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryTransaction
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config
import java.util.LinkedList
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

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
        SentryAndroid.init(context) {
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
        Sentry.configureScope { scope ->
            sid = scope.session?.sessionId.toString()
        }
        return sid
    }

    private fun setupLifecycle(options: SentryOptions): LifecycleRegistry {
        val lifecycle = LifecycleRegistry(mock())
        val lifecycleWatcher = (
            options.integrations.find {
                it is AppLifecycleIntegration
            } as AppLifecycleIntegration
            ).watcher
        lifecycle.addObserver(lifecycleWatcher!!)
        return lifecycle
    }

    private class CapturingSentryClient : ISentryClient {
        val sessionUpdates = LinkedList<Session>()

        override fun isEnabled(): Boolean = true

        override fun captureEvent(event: SentryEvent, scope: Scope?, hint: Hint?): SentryId {
            TODO("Not yet implemented")
        }

        override fun close() {
            TODO("Not yet implemented")
        }

        override fun flush(timeoutMillis: Long) {
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
            scope: Scope?,
            hint: Hint?,
            profilingTraceData: ProfilingTraceData?
        ): SentryId {
            TODO("Not yet implemented")
        }
    }
}
