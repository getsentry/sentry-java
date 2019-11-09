package io.sentry.core.transport

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import io.sentry.core.SentryEvent
import io.sentry.core.cache.IEventCache
import io.sentry.core.protocol.SentryThread
import kotlin.test.Test

class CrashedEventStoreTest {

    class Fixture {
        val connection = mock<Connection>()
        val eventCache = mock<IEventCache>()
        fun getSut() = CrashedEventStore(connection, eventCache)
    }

    private val fixture = Fixture()

    @Test
    fun `when event includes a crashed thread, event is persisted`() {
        val sut = fixture.getSut()
        val actual = SentryEvent().apply {
            threads = listOf(SentryThread().apply { isCrashed = true })
        }
        sut.send(actual)
        verify(fixture.eventCache).store(actual)
        verify(fixture.connection, never()).send(any())
    }

    @Test
    fun `when event doesn't include a crashed thread, event is passed to inner connection`() {
        val sut = fixture.getSut()
        val actual = SentryEvent().apply {
            threads = listOf(SentryThread().apply { isCrashed = false })
        }
        sut.send(actual)
        verify(fixture.connection).send(actual)
        verify(fixture.eventCache, never()).store(actual)
    }
}
