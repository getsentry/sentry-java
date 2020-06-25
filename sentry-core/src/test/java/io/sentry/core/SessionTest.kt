package io.sentry.core

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.protocol.User
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionTest {

    @Test
    fun `when starting a session, set param and default values`() {
        val user = User().apply {
            id = "123"
            ipAddress = "127.0.0.1"
        }
        val session = createSession(user)
        assertEquals("rel", session.release)
        assertEquals("env", session.environment)
        assertEquals("distinctId", session.distinctId)
        assertEquals("127.0.0.1", session.ipAddress)
        assertTrue(session.init!!)
        assertEquals(0L, session.sequence)
        assertNotNull(session.sessionId)
        assertNotNull(session.started)
        assertEquals(Session.State.Ok, session.status)
    }

    @Test
    fun `when ending a session, reset init values and stop session`() {
        val user = User().apply {
            ipAddress = "127.0.0.1"
        }
        val session = createSession(user)
        val timestamp = session.started

        session.end()
        assertNull(session.init)
        assertTrue(session.timestamp!! >= timestamp)
        assertNotNull(session.duration)
        assertTrue(session.sequence!! > 0L)
    }

    @Test
    fun `when ending a session, if status is ok and no errorCount, mark it as exited`() {
        val user = User().apply {
            ipAddress = "127.0.0.1"
        }
        val session = createSession(user)
        session.end()
        assertEquals(Session.State.Exited, session.status)
    }

    @Test
    fun `when ending a session, if status is ok, mark it as exited`() {
        val user = User().apply {
            ipAddress = "127.0.0.1"
        }
        val session = createSession(user)
        session.update(null, null, true)
        session.end()
        assertEquals(Session.State.Exited, session.status)
    }

    @Test
    fun `when ending a session, if status is crashed, keep as it is`() {
        val user = User().apply {
            ipAddress = "127.0.0.1"
        }
        val session = createSession(user)
        session.update(Session.State.Crashed, null, true)
        session.end()
        assertEquals(Session.State.Crashed, session.status)
    }

    @Test
    fun `when ending a session, if theres a timestamp, use it`() {
        val user = User().apply {
            ipAddress = "127.0.0.1"
        }
        val session = createSession(user)
        val date = Date()
        session.update(Session.State.Crashed, null, true)
        session.end(date)
        assertEquals(date, session.timestamp)
    }

    @Test
    fun `when updating a session, set default values`() {
        val user = User().apply {
            ipAddress = "127.0.0.1"
        }
        val session = createSession(user)
        val timestamp = session.started
        val sequecence = session.sequence
        session.update(null, null, true)

        assertNull(session.init)
        assertTrue(session.timestamp!! >= timestamp)
        assertTrue(session.sequence!! > sequecence!!)
    }

    @Test
    fun `Offset sequence if Date and Time is wrong and time is negative`() {
        val user = User().apply {
            ipAddress = "127.0.0.1"
        }
        val session = createSession(user)
        val dateMock = mock<Date>()
        whenever(dateMock.time).thenReturn(-1489552)
        session.end(dateMock)
        assertEquals(1489552, session.sequence)
    }

    private fun createSession(user: User = User()): Session {
        return Session("distinctId", user, "env", "rel")
    }
}
