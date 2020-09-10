package io.sentry.cache

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.ILogger
import io.sentry.ISerializer
import io.sentry.SentryEnvelope
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.Session
import io.sentry.cache.EnvelopeCache.PREFIX_CURRENT_SESSION_FILE
import io.sentry.cache.EnvelopeCache.SUFFIX_CURRENT_SESSION_FILE
import io.sentry.hints.SessionEndHint
import io.sentry.hints.SessionStartHint
import io.sentry.protocol.User
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EnvelopeCacheTest {
    private class Fixture {
        val maxSize = 5
        val dir: Path = Files.createTempDirectory("sentry-session-cache-test")
        val serializer = mock<ISerializer>()
        val options = SentryOptions()
        val logger = mock<ILogger>()

        fun getSUT(): IEnvelopeCache {
            options.cacheDirPath = dir.toAbsolutePath().toFile().absolutePath

            whenever(serializer.deserializeSession(any())).thenAnswer {
                Session("dis", User(), "env", "rel")
            }

            options.setLogger(logger)
            options.setSerializer(serializer)
            options.isDebug = true

            return EnvelopeCache(options)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `stores envelopes`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.cacheDirPath!!)
        val nofFiles = { file.list()?.size }

        assertEquals(0, nofFiles())

        cache.store(SentryEnvelope.fromSession(fixture.serializer, createSession(), null))

        assertEquals(1, nofFiles())

        file.deleteRecursively()
    }

    @Test
    fun `tolerates discarding unknown envelope`() {
        val cache = fixture.getSUT()

        cache.discard(SentryEnvelope.fromSession(fixture.serializer, createSession(), null))

        // no exception thrown
    }

    @Test
    fun `creates current file on session start`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.cacheDirPath!!)

        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession(), null)
        cache.store(envelope, SessionStartHint())

        val currentFile = File(fixture.options.cacheDirPath!!, "$PREFIX_CURRENT_SESSION_FILE$SUFFIX_CURRENT_SESSION_FILE")
        assertTrue(currentFile.exists())

        file.deleteRecursively()
    }

    @Test
    fun `deletes current file on session end`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.cacheDirPath!!)

        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession(), null)
        cache.store(envelope, SessionStartHint())

        val currentFile = File(fixture.options.cacheDirPath!!, "$PREFIX_CURRENT_SESSION_FILE$SUFFIX_CURRENT_SESSION_FILE")
        assertTrue(currentFile.exists())

        cache.store(envelope, SessionEndHint())
        assertFalse(currentFile.exists())

        file.deleteRecursively()
    }

    @Test
    fun `updates current file on session update and read it back`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.cacheDirPath!!)

        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession(), null)
        cache.store(envelope, SessionStartHint())

        val currentFile = File(fixture.options.cacheDirPath!!, "$PREFIX_CURRENT_SESSION_FILE$SUFFIX_CURRENT_SESSION_FILE")
        assertTrue(currentFile.exists())

        val session = fixture.serializer.deserializeSession(currentFile.bufferedReader(Charsets.UTF_8))
        assertNotNull(session)

        currentFile.delete()

        file.deleteRecursively()
    }

    @Test
    fun `when session start and current file already exist, close session and start a new one`() {
        val cache = fixture.getSUT()

        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession(), null)
        cache.store(envelope, SessionStartHint())

        val newEnvelope = SentryEnvelope.fromSession(fixture.serializer, createSession(), null)

        cache.store(newEnvelope, SessionStartHint())
        verify(fixture.logger).log(eq(SentryLevel.WARNING), eq("Current session is not ended, we'd need to end it."))
    }

    @Test
    fun `when session start, current file already exist and crash marker file exist, end session and delete marker file`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.cacheDirPath!!)
        val markerFile = File(fixture.options.cacheDirPath!!, EnvelopeCache.CRASH_MARKER_FILE)
        markerFile.mkdirs()
        assertTrue(markerFile.exists())

        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession(), null)
        cache.store(envelope, SessionStartHint())

        val newEnvelope = SentryEnvelope.fromSession(fixture.serializer, createSession(), null)

        cache.store(newEnvelope, SessionStartHint())
        verify(fixture.logger).log(eq(SentryLevel.INFO), eq("Crash marker file exists, last Session is gonna be Crashed."))
        assertFalse(markerFile.exists())
        file.deleteRecursively()
    }

    @Test
    fun `when session start, current file already exist and crash marker file exist, end session with given timestamp`() {
        val cache = fixture.getSUT()
        val file = File(fixture.options.cacheDirPath!!)
        val markerFile = File(fixture.options.cacheDirPath!!, EnvelopeCache.CRASH_MARKER_FILE)
        File(fixture.options.cacheDirPath!!, ".sentry-native").mkdirs()
        markerFile.createNewFile()
        val date = "2020-02-07T14:16:00.000Z"
        markerFile.writeText(charset = Charsets.UTF_8, text = date)
        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession(), null)
        cache.store(envelope, SessionStartHint())

        val newEnvelope = SentryEnvelope.fromSession(fixture.serializer, createSession(), null)

        cache.store(newEnvelope, SessionStartHint())
        assertFalse(markerFile.exists())
        file.deleteRecursively()
        File(fixture.options.cacheDirPath!!).deleteRecursively()
    }

    private fun createSession(): Session {
        return Session("dis", User(), "env", "rel")
    }
}
