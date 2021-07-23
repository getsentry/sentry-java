package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SessionAggregatesTest {

    private class Fixture {
        fun getSut(release: String = "0.0.1", environment: String? = null) = SessionAggregates(release, environment)
    }

    private val fixture = Fixture()

    @Test
    fun `sets attributes`() {
        val sut = fixture.getSut(release = "1.0.0", environment = "prod")
        assertEquals("1.0.0", sut.attributes.release)
        assertEquals("prod", sut.attributes.environment)
    }

    @Test
    fun `adds crashed session`() {
        val sut = fixture.getSut()
        val session = createSession("2021-12-01T13:11:09Z", Session.State.Crashed)
        session.end()
        sut.addSession(session.started!!, ServerSessionManager.Status.Crashed)
        assertNotNull(sut.aggregates["2021-12-01T13:11:00Z"]) {
            assertEquals(1, it.crashed.get())
            assertEquals(0, it.errored.get())
            assertEquals(0, it.exited.get())
        }
    }

    @Test
    fun `adds exited session`() {
        val sut = fixture.getSut()
        val session = createSession("2021-12-01T13:11:09Z", Session.State.Exited)
        session.end()
        sut.addSession(session.started!!, ServerSessionManager.Status.Exited)
        assertNotNull(sut.aggregates["2021-12-01T13:11:00Z"]) {
            assertEquals(0, it.errored.get())
            assertEquals(1, it.exited.get())
        }
    }

    private fun createSession(startedAt: String, status: Session.State, release: String = "release") =
        Session(status, DateUtils.getDateTime(startedAt), null, 0, null, null, null, null, null, null, null, null, release)
}
