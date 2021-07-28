package io.sentry

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertTrue
import org.awaitility.kotlin.await

class SessionsFlusherTest {

    private class Fixture {
        val client = mock<ISentryClient>()
        lateinit var aggregates: SessionAggregates

        fun getSut(aggregates: SessionAggregates = SessionAggregates("", ""), delay: Long? = null, period: Long? = null): SessionsFlusher {
            this.aggregates = aggregates
            return if (delay != null && period != null) {
                SessionsFlusher(aggregates, client, delay, period)
            } else {
                SessionsFlusher(aggregates, client)
            }
        }
    }

    private val fixture = Fixture()

    @Test
    fun `if aggregates is empty, does not send sessions`() {
        val flusher = fixture.getSut()
        flusher.flush()
        verifyZeroInteractions(fixture.client)
    }

    @Test
    fun `if aggregates is not empty, flushes sessions and clears aggregates`() {
        val flusher = fixture.getSut(SessionAggregates("", "").apply {
            this.addSession(ServerSessionManager.Status.Exited)
        })
        flusher.flush()
        verify(fixture.client).captureSessions(check {
            assertTrue(it.aggregates.isNotEmpty())
        })
        assertTrue(fixture.aggregates.aggregates.isEmpty())
    }

    @Test
    fun `schedules a timer`() {
        val flusher = fixture.getSut(aggregates = SessionAggregates("", "").apply {
            this.addSession(ServerSessionManager.Status.Exited)
        }, delay = 2, period = 1000)
        flusher.start()
        flusher.use {
            await.untilAsserted {
                verify(fixture.client).captureSessions(any())
            }
        }
    }

    @Test
    fun `stops a timer on close`() {
        val flusher = fixture.getSut(delay = 2, period = 20)
        flusher.start()
        flusher.close()
        fixture.aggregates.addSession(ServerSessionManager.Status.Exited)

        flusher.use {
            await.during(Duration.ofMillis(25)).untilAsserted {
                verifyZeroInteractions(fixture.client)
            }
        }
    }
}
