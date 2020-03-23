package io.sentry.core.cache

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.Hub
import io.sentry.core.ILogger
import io.sentry.core.ISerializer
import io.sentry.core.SentryEnvelope
import io.sentry.core.SentryLevel
import io.sentry.core.SentryOptions
import io.sentry.core.Session
import io.sentry.core.cache.SessionCache.PREFIX_CURRENT_SESSION_FILE
import io.sentry.core.cache.SessionCache.SUFFIX_CURRENT_SESSION_FILE
import io.sentry.core.cache.SessionCache.SUFFIX_ENVELOPE_FILE
import io.sentry.core.protocol.User
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionCacheTest {
    private class Fixture {
        val maxSize = 5
        val dir: Path = Files.createTempDirectory("sentry-session-cache-test")
        val serializer = mock<ISerializer>()
        val options = SentryOptions()
        val logger = mock<ILogger>()

        fun getSUT(): ISessionCache {
            options.sessionsDirSize = maxSize
            options.cacheDirPath = dir.toAbsolutePath().toFile().absolutePath
            File(options.sessionsPath!!).mkdirs()

            whenever(serializer.deserializeSession(any())).thenAnswer {
                val session = Session()
                session
            }

            options.setLogger(logger)
            options.setSerializer(serializer)
            options.isDebug = true

            return SessionCache(options)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `stores envelopes`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.sessionsPath!!)
        val nofFiles = { file.list()?.size }

        assertEquals(0, nofFiles())

        cache.store(SentryEnvelope.fromSession(fixture.serializer, createSession()))

        assertEquals(1, nofFiles())

        deleteFiles(file)
    }

    @Test
    fun `limits the number of stored envelopes`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.sessionsPath!!)
        val nofFiles = { file.list()?.size }

        assertEquals(0, nofFiles())

        (1..fixture.maxSize + 1).forEach { _ ->
            cache.store(SentryEnvelope.fromSession(fixture.serializer, createSession()))
        }

        assertEquals(fixture.maxSize, nofFiles())

        deleteFiles(file)
    }

    @Test
    fun `tolerates discarding unknown envelope`() {
        val cache = fixture.getSUT()

        cache.discard(SentryEnvelope.fromSession(fixture.serializer, createSession()))

        // no exception thrown
    }

    @Test
    fun `creates current file on session start`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.sessionsPath!!)

        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession())
        cache.store(envelope, Hub.SessionStartHint())

        val currentFile = File(fixture.options.sessionsPath!!, "$PREFIX_CURRENT_SESSION_FILE$SUFFIX_CURRENT_SESSION_FILE")
        assertTrue(currentFile.exists())

        deleteFiles(file)
    }

    @Test
    fun `deletes current file on session end`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.sessionsPath!!)

        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession())
        cache.store(envelope, Hub.SessionStartHint())

        val currentFile = File(fixture.options.sessionsPath!!, "$PREFIX_CURRENT_SESSION_FILE$SUFFIX_CURRENT_SESSION_FILE")
        assertTrue(currentFile.exists())

        cache.store(envelope, Hub.SessionEndHint())
        assertFalse(currentFile.exists())

        deleteFiles(file)
    }

    @Test
    fun `updates current file on session update, but do not create a new envelope`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.sessionsPath!!)

        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession())
        cache.store(envelope, Hub.SessionStartHint())

        val currentFile = File(fixture.options.sessionsPath!!, "$PREFIX_CURRENT_SESSION_FILE$SUFFIX_CURRENT_SESSION_FILE")
        assertTrue(currentFile.exists())

        val newEnvelope = SentryEnvelope.fromSession(fixture.serializer, createSession())

        cache.store(newEnvelope, Hub.SessionUpdateHint())
        assertTrue(currentFile.exists())

        val newFile = File(file.absolutePath, "${newEnvelope.header.eventId}$SUFFIX_ENVELOPE_FILE")
        assertFalse(newFile.exists())

        deleteFiles(file)
    }

    @Test
    fun `when session start and current file already exist, close session and start a new one`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.sessionsPath!!)

        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession())
        cache.store(envelope, Hub.SessionStartHint())

        val newEnvelope = SentryEnvelope.fromSession(fixture.serializer, createSession())

        cache.store(newEnvelope, Hub.SessionStartHint())
        verify(fixture.logger).log(eq(SentryLevel.INFO), eq("There's a left over session, it's gonna be ended and cached to be sent."))

        deleteFiles(file)
    }

    private fun deleteFiles(file: File) {
        file.listFiles()?.forEach { it.delete() }
        Files.delete(file.toPath())
    }

    fun createSession(): Session {
        val session = Session()
        session.start("rel", "env", User(), "dis")
        return session
    }
}
