package io.sentry.protocol

import io.sentry.DateUtils
import io.sentry.ServerSessionManager
import io.sentry.SessionAggregates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionsTest {

    @Test
    fun `creates sessions from empty aggregates`() {
        val aggregates = SessionAggregates("release", "env")
        val sessions = Sessions(aggregates)
        assertTrue(sessions.aggregates.isEmpty())
        assertEquals("release", sessions.attrs.release)
        assertEquals("env", sessions.attrs.environment)
    }

    @Test
    fun `creates sessions from aggregates`() {
        val aggregates = SessionAggregates("release", "env")

        with(DateUtils.getDateTime("2020-12-12T12:13:11Z")) {
            aggregates.addSession(this, ServerSessionManager.Status.Exited)
            aggregates.addSession(this, ServerSessionManager.Status.Errored)
        }
        with(DateUtils.getDateTime("2020-12-12T12:14:11Z")) {
            aggregates.addSession(this, ServerSessionManager.Status.Errored)
            aggregates.addSession(this, ServerSessionManager.Status.Crashed)
        }
        with(DateUtils.getDateTime("2020-12-12T12:15:11Z")) {
            aggregates.addSession(this, ServerSessionManager.Status.Crashed)
            aggregates.addSession(this, ServerSessionManager.Status.Crashed)
        }

        val sessions = Sessions(aggregates)

        assertEquals(3, sessions.aggregates.size)
        sessions.aggregates.sortBy { it.started }
        with(sessions.aggregates[0]) {
            assertEquals("2020-12-12T12:13:00Z", this.started)
            assertEquals(1, this.exited)
            assertEquals(1, this.errored)
            assertEquals(0, this.crashed)
        }
        with(sessions.aggregates[1]) {
            assertEquals("2020-12-12T12:14:00Z", this.started)
            assertEquals(0, this.exited)
            assertEquals(1, this.errored)
            assertEquals(1, this.crashed)
        }
        with(sessions.aggregates[2]) {
            assertEquals("2020-12-12T12:15:00Z", this.started)
            assertEquals(0, this.exited)
            assertEquals(0, this.errored)
            assertEquals(2, this.crashed)
        }
    }
}
