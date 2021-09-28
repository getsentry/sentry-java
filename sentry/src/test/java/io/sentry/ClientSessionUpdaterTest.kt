package io.sentry

import io.sentry.protocol.Mechanism
import io.sentry.protocol.SentryException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ClientSessionUpdaterTest {

    private class Fixture {

        val sentryOptions = SentryOptions().apply {
            release = "0.0.1"
        }

        fun getSut(): ClientSessionUpdater {
            return ClientSessionUpdater(sentryOptions)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `When capture an event and there's no session, do nothing`() {
        val scope = Scope(fixture.sentryOptions)
        val event = SentryEvent()
        fixture.getSut().updateSessionData(event, null, scope)
        scope.withSession {
            assertNull(it)
        }
    }

    @Test
    fun `When event is non handled, increase errorCount`() {
        val scope = Scope(fixture.sentryOptions)
        scope.startSession()
        val event = SentryEvent().apply {
            exceptions = createNonHandledException()
        }
        fixture.getSut().updateSessionData(event, null, scope)
        scope.withSession {
            assertEquals(1, it!!.errorCount())
        }
    }

    @Test
    fun `When event is Errored, increase errorCount`() {
        val scope = Scope(fixture.sentryOptions)
        scope.startSession()
        val exceptions = mutableListOf<SentryException>()
        exceptions.add(SentryException())
        val event = SentryEvent().apply {
            setExceptions(exceptions)
        }
        fixture.getSut().updateSessionData(event, null, scope)
        scope.withSession {
            assertEquals(1, it!!.errorCount())
        }
    }

    @Test
    fun `When event is handled and not errored, do not increase errorsCount`() {
        val scope = Scope(fixture.sentryOptions)
        val sessionPair = scope.startSession()
        assertNotNull(sessionPair) {
            val session = it.current
            val errorCount = session.errorCount()
            val event = SentryEvent()
            fixture.getSut().updateSessionData(event, null, scope)
            assertEquals(errorCount, session.errorCount())
        }
    }

    @Test
    fun `When event is non handled, mark session as Crashed`() {
        val scope = Scope(fixture.sentryOptions)
        scope.startSession()

        val event = SentryEvent().apply {
            exceptions = createNonHandledException()
        }
        fixture.getSut().updateSessionData(event, null, scope)
        scope.withSession {
            assertEquals(Session.State.Crashed, it!!.status)
        }
    }

    @Test
    fun `When event is handled, keep level as it is`() {
        val scope = Scope(fixture.sentryOptions)
        val sessionPair = scope.startSession()
        assertNotNull(sessionPair) {
            val session = it.current
            val level = session.status
            val event = SentryEvent()
            fixture.getSut().updateSessionData(event, null, scope)
            assertEquals(level, session.status)
        }
    }

    private fun createNonHandledException(): List<SentryException> {
        val exception = SentryException().apply {
            mechanism = Mechanism().apply {
                isHandled = false
            }
        }
        return listOf(exception)
    }
}
