package io.sentry.core

import io.sentry.core.protocol.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionTest {

    @Test
    fun `when starting a session, set param and default values`() {
        val session = Session()
        val user = User().apply {
            id = "123"
            ipAddress = "127.0.0.1"
        }
        session.start("rel", "env", user)
        assertEquals("rel", session.release)
        assertEquals("env", session.environment)
        assertEquals("123", session.deviceId)
        assertEquals("127.0.0.1", session.ipAddress)
        assertTrue(session.init)
        assertEquals(0L, session.sequence)
        assertNotNull(session.sessionId)
        assertNotNull(session.started)
        assertEquals(Session.State.Ok, session.status)
    }

    @Test
    fun `when ending a session, reset init values and stop session`() {
        val session = Session()
        val user = User().apply {
            id = "123"
            ipAddress = "127.0.0.1"
        }
        session.start("rel", "env", user)
        val timestamp = session.started

        session.end()
        assertNull(session.init)
        assertTrue(session.timestamp >= timestamp)
        assertNotNull(session.duration)
        assertTrue(session.sequence > 0L)
    }

    @Test
    fun `when ending a session, if status is ok and no errorCount, mark it as exited`() {
        val session = Session()
        val user = User().apply {
            id = "123"
            ipAddress = "127.0.0.1"
        }
        session.start("rel", "env", user)
        session.end()
        assertEquals(Session.State.Exited, session.status)
    }

    @Test
    fun `when ending a session, if status is ok and has errorCount, mark it as abnormal`() {
        val session = Session()
        val user = User().apply {
            id = "123"
            ipAddress = "127.0.0.1"
        }
        session.start("rel", "env", user)
        session.update(null, null, true)
        session.end()
        assertEquals(Session.State.Abnormal, session.status)
    }

    @Test
    fun `when ending a session, if status is crashed, keep as it is`() {
        val session = Session()
        val user = User().apply {
            id = "123"
            ipAddress = "127.0.0.1"
        }
        session.start("rel", "env", user)
        session.update(Session.State.Crashed, null, true)
        session.end()
        assertEquals(Session.State.Crashed, session.status)
    }

    @Test
    fun `when updating a session, set default values`() {
        val session = Session()
        val user = User().apply {
            id = "123"
            ipAddress = "127.0.0.1"
        }
        session.start("rel", "env", user)
        val timestamp = session.started
        val sequecence = session.sequence
        session.update(null, null, true)

        assertNull(session.init)
        assertTrue(session.timestamp >= timestamp)
        assertTrue(session.sequence > sequecence)
    }
}
