package io.sentry

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.protocol.Mechanism
import io.sentry.protocol.SentryException
import kotlin.test.Test

class ServerSessionManagerTest {

    private class Fixture {
        val aggregates = mock<SessionAggregates>()

        fun getSut(): ServerSessionManager {
            return ServerSessionManager(aggregates)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when no events triggered, ending session adds exited to aggregate`() {
        val sut = fixture.getSut()
        executeSession(sut)
        verify(fixture.aggregates).addSession(ServerSessionManager.Status.Exited)
    }

    @Test
    fun `when crashed event triggered, ending session adds crashed to aggregate`() {
        val sut = fixture.getSut()
        executeSession(sut, crashedEvent())
        verify(fixture.aggregates).addSession(ServerSessionManager.Status.Crashed)
    }

    @Test
    fun `when errored event triggered, ending session adds errored to aggregate`() {
        val sut = fixture.getSut()
        executeSession(sut, erroredEvent())
        verify(fixture.aggregates).addSession(ServerSessionManager.Status.Errored)
    }

    @Test
    fun `when regular event triggered, ending session adds exited to an aggregate`() {
        val sut = fixture.getSut()
        executeSession(sut, SentryEvent())
        verify(fixture.aggregates).addSession(ServerSessionManager.Status.Exited)
    }

    private fun erroredEvent() =
        SentryEvent().apply {
            exceptions = listOf(
                SentryException().apply {
                    this.value = "val"
                }
            )
        }

    private fun crashedEvent() = SentryEvent().apply {
        this.exceptions = mutableListOf(
            SentryException().apply {
                mechanism = Mechanism().apply {
                    this.isHandled = false
                }
            }
        )
    }

    private fun executeSession(sessionManager: ServerSessionManager, event: SentryEvent? = null) {
        sessionManager.startSession()
        event?.let {
            sessionManager.updateSessionData(it, Hint(), null)
        }
        sessionManager.endSession()
    }
}
