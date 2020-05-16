package io.sentry.core.cache

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.ILogger
import io.sentry.core.ISerializer
import io.sentry.core.SentryEnvelope
import io.sentry.core.SentryLevel
import io.sentry.core.SentryOptions
import io.sentry.core.Session
import io.sentry.core.cache.SessionCache.PREFIX_CURRENT_SESSION_FILE
import io.sentry.core.cache.SessionCache.SUFFIX_CURRENT_SESSION_FILE
import io.sentry.core.cache.SessionCache.SUFFIX_ENVELOPE_FILE
import io.sentry.core.hints.SessionEndHint
import io.sentry.core.hints.SessionStartHint
import io.sentry.core.hints.SessionUpdateHint
import io.sentry.core.protocol.User
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionCacheTest {
    private class Fixture {
        val maxSize = 5
        val dir: Path = Files.createTempDirectory("sentry-session-cache-test")
        val serializer = mock<ISerializer>()
        val options = SentryOptions()
        val logger = mock<ILogger>()

        fun getSUT(): IEnvelopeCache {
            options.sessionsDirSize = maxSize
            options.cacheDirPath = dir.toAbsolutePath().toFile().absolutePath
            File(options.sessionsPath!!).mkdirs()

            whenever(serializer.deserializeSession(any())).thenAnswer {
                Session("dis", User(), "env", "rel")
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

        file.deleteRecursively()
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

        file.deleteRecursively()
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
        cache.store(envelope, SessionStartHint())

        val currentFile = File(fixture.options.sessionsPath!!, "$PREFIX_CURRENT_SESSION_FILE$SUFFIX_CURRENT_SESSION_FILE")
        assertTrue(currentFile.exists())

        file.deleteRecursively()
    }

    @Test
    fun `deletes current file on session end`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.sessionsPath!!)

        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession())
        cache.store(envelope, SessionStartHint())

        val currentFile = File(fixture.options.sessionsPath!!, "$PREFIX_CURRENT_SESSION_FILE$SUFFIX_CURRENT_SESSION_FILE")
        assertTrue(currentFile.exists())

        cache.store(envelope, SessionEndHint())
        assertFalse(currentFile.exists())

        file.deleteRecursively()
    }

    @Test
    fun `updates current file on session update, but do not create a new envelope`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.sessionsPath!!)

        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession())
        cache.store(envelope, SessionStartHint())

        val currentFile = File(fixture.options.sessionsPath!!, "$PREFIX_CURRENT_SESSION_FILE$SUFFIX_CURRENT_SESSION_FILE")
        assertTrue(currentFile.exists())

        val newEnvelope = SentryEnvelope.fromSession(fixture.serializer, createSession())

        cache.store(newEnvelope, SessionUpdateHint())
        assertTrue(currentFile.exists())

        val newFile = File(file.absolutePath, "${newEnvelope.header.eventId}$SUFFIX_ENVELOPE_FILE")
        assertFalse(newFile.exists())

        file.deleteRecursively()
    }

    @Test
    fun `updates current file on session update and read it back`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.sessionsPath!!)

        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession())
        cache.store(envelope, SessionStartHint())

        val currentFile = File(fixture.options.sessionsPath!!, "$PREFIX_CURRENT_SESSION_FILE$SUFFIX_CURRENT_SESSION_FILE")
        assertTrue(currentFile.exists())

        val session = fixture.serializer.deserializeSession(currentFile.bufferedReader(Charsets.UTF_8))
        assertNotNull(session)

        currentFile.delete()

        file.deleteRecursively()
    }

    @Test
    fun `when session start and current file already exist, close session and start a new one`() {
        val cache = fixture.getSUT()

        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession())
        cache.store(envelope, SessionStartHint())

        val newEnvelope = SentryEnvelope.fromSession(fixture.serializer, createSession())

        cache.store(newEnvelope, SessionStartHint())
        verify(fixture.logger).log(eq(SentryLevel.WARNING), eq("Current session is not ended, we'd need to end it."))
    }

    @Test
    fun `when session start, current file already exist and crash marker file exist, end session and delete marker file`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.sessionsPath!!)
        val markerFile = File(fixture.options.cacheDirPath!!, SessionCache.CRASH_MARKER_FILE)
        markerFile.mkdirs()
        assertTrue(markerFile.exists())

        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession())
        cache.store(envelope, SessionStartHint())

        val newEnvelope = SentryEnvelope.fromSession(fixture.serializer, createSession())

        cache.store(newEnvelope, SessionStartHint())
        verify(fixture.logger).log(eq(SentryLevel.INFO), eq("Crash marker file exists, last Session is gonna be Crashed."))
        assertFalse(markerFile.exists())
        file.deleteRecursively()
    }

    @Test
    fun `when session start, current file already exist and crash marker file exist, end session with given timestamp`() {
        val cache = fixture.getSUT()
        val file = File(fixture.options.sessionsPath!!)
        val markerFile = File(fixture.options.cacheDirPath!!, SessionCache.CRASH_MARKER_FILE)
        File(fixture.options.cacheDirPath!!, ".sentry-native").mkdirs()
        markerFile.createNewFile()
        val date = "2020-02-07T14:16:00.000Z"
        markerFile.writeText(charset = Charsets.UTF_8, text = date)
        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession())
        cache.store(envelope, SessionStartHint())

        val newEnvelope = SentryEnvelope.fromSession(fixture.serializer, createSession())

        cache.store(newEnvelope, SessionStartHint())
        assertFalse(markerFile.exists())
        file.deleteRecursively()
        File(fixture.options.cacheDirPath!!).deleteRecursively()
    }

    private fun createSession(): Session {
        return Session("dis", User(), "env", "rel")
    }
}
