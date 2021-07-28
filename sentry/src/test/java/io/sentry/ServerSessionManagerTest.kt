package io.sentry

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.protocol.Mechanism
import io.sentry.protocol.SentryException
import kotlin.test.Test

class ServerSessionManagerTest {

    @Test
    fun `when no events triggered, ending session adds exited to aggregate`() {
        val aggregates = mock<SessionAggregates>()
        val sessionManager = ServerSessionManager(aggregates)
        sessionManager.startSession()
        sessionManager.endSession()
        verify(aggregates).addSession(ServerSessionManager.Status.Exited)
    }

    @Test
    fun `when crashed event triggered, ending session adds crashed to aggregate`() {
        val aggregates = mock<SessionAggregates>()
        val sessionManager = ServerSessionManager(aggregates)
        val event = crashedEvent()

        sessionManager.startSession()
        sessionManager.updateSessionData(event, null, null)
        sessionManager.endSession()
        verify(aggregates).addSession(ServerSessionManager.Status.Crashed)
    }

    @Test
    fun `when regular event triggered, ending session adds errored to aggregate`() {
        val aggregates = mock<SessionAggregates>()
        val sessionManager = ServerSessionManager(aggregates)
        val event = SentryEvent()

        sessionManager.startSession()
        sessionManager.updateSessionData(event, null, null)
        sessionManager.endSession()
        verify(aggregates).addSession(ServerSessionManager.Status.Errored)
    }

    private fun crashedEvent() = SentryEvent().apply {
        this.exceptions = mutableListOf(SentryException().apply {
            mechanism = Mechanism().apply {
                this.isHandled = false
            }
        })
    }
}
