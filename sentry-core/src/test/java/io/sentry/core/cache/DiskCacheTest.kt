package io.sentry.core.cache

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.ISerializer
import io.sentry.core.SentryEvent
import io.sentry.core.SentryOptions
import io.sentry.core.protocol.SentryId
import java.io.Reader
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DiskCacheTest {
    private class Fixture {
        val maxSize = 5
        val dir: Path = Files.createTempDirectory("sentry-disk-cache-test")

        fun getSUT(): DiskCache {
            val options = SentryOptions()
            options.cacheDirSize = maxSize
            options.cacheDirPath = dir.toAbsolutePath().toFile().absolutePath

            val serializer = mock<ISerializer>()
            doAnswer {
                val event = it.arguments[0] as SentryEvent
                val writer = it.arguments[1] as Writer

                writer.write(event.eventId.toString())
            }.whenever(serializer).serialize(any(), any())

            whenever(serializer.deserializeEvent(any())).thenAnswer {
                val reader = it.arguments[0] as Reader

                val ret = SentryEvent()
                val text = reader.readText()
                ret.eventId = SentryId(text)
                ret
            }

            options.setSerializer(serializer)

            return DiskCache(options)
        }
    }

    private val fixture = Fixture()

    @AfterTest
    fun cleanUp() {
        fixture.dir.toFile().listFiles()?.forEach { it.delete() }
        Files.delete(fixture.dir)
    }

    @Test
    fun `stores events`() {
        val cache = fixture.getSUT()

        val nofFiles = { fixture.dir.toFile().list()?.size }

        assertEquals(0, nofFiles())

        cache.store(SentryEvent())

        assertEquals(1, nofFiles())
    }

    @Test
    fun `limits the number of stored events`() {
        val cache = fixture.getSUT()

        val nofFiles = { fixture.dir.toFile().list()?.size }

        assertEquals(0, nofFiles())

        (1..fixture.maxSize + 1).forEach { _ ->
            cache.store(SentryEvent(Exception()))
        }

        assertEquals(fixture.maxSize, nofFiles())
    }

    @Test
    fun `tolerates discarding unknown event`() {
        val cache = fixture.getSUT()

        cache.discard(SentryEvent())

        // no exception thrown
    }

    @Test
    fun `reads the event back`() {

        val cache = fixture.getSUT()

        val event = SentryEvent()

        cache.store(event)

        val readEvents = cache.toList()

        assertEquals(1, readEvents.size)

        val readEvent = readEvents[0]

        assertEquals(event.eventId.toString(), readEvent.eventId.toString())
    }
}
