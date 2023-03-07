package io.sentry.cache

import io.sentry.ILogger
import io.sentry.ISerializer
import io.sentry.NoOpLogger
import io.sentry.SentryCrashLastRunState
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.Session
import io.sentry.UncaughtExceptionHandlerIntegration.UncaughtExceptionHint
import io.sentry.cache.EnvelopeCache.PREFIX_CURRENT_SESSION_FILE
import io.sentry.cache.EnvelopeCache.SUFFIX_CURRENT_SESSION_FILE
import io.sentry.hints.DiskFlushNotification
import io.sentry.hints.SessionEndHint
import io.sentry.hints.SessionStartHint
import io.sentry.protocol.User
import io.sentry.util.HintUtils
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EnvelopeCacheTest {
    private class Fixture {
        val dir: Path = Files.createTempDirectory("sentry-session-cache-test")
        val serializer = mock<ISerializer>()
        val options = SentryOptions()
        val logger = mock<ILogger>()

        fun getSUT(): IEnvelopeCache {
            options.cacheDirPath = dir.toAbsolutePath().toFile().absolutePath

            whenever(serializer.deserialize(any(), eq(Session::class.java))).thenAnswer {
                Session("dis", User(), "env", "rel")
            }

            options.setLogger(logger)
            options.setSerializer(serializer)
            options.setDebug(true)

            return EnvelopeCache.create(options)
        }
    }

    private val fixture = Fixture()

    @BeforeTest
    fun `set up`() {
        SentryCrashLastRunState.getInstance().reset()
    }

    @Test
    fun `stores envelopes`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.cacheDirPath!!)
        val nofFiles = { file.list()?.size }

        assertEquals(0, nofFiles())

        cache.store(SentryEnvelope.from(fixture.serializer, createSession(), null))

        assertEquals(1, nofFiles())

        file.deleteRecursively()
    }

    @Test
    fun `tolerates discarding unknown envelope`() {
        val cache = fixture.getSUT()

        cache.discard(SentryEnvelope.from(fixture.serializer, createSession(), null))

        // no exception thrown
    }

    @Test
    fun `creates current file on session start`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.cacheDirPath!!)

        val envelope = SentryEnvelope.from(fixture.serializer, createSession(), null)

        val hints = HintUtils.createWithTypeCheckHint(SessionStartHint())
        cache.store(envelope, hints)

        val currentFile = File(fixture.options.cacheDirPath!!, "$PREFIX_CURRENT_SESSION_FILE$SUFFIX_CURRENT_SESSION_FILE")
        assertTrue(currentFile.exists())

        file.deleteRecursively()
    }

    @Test
    fun `deletes current file on session end`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.cacheDirPath!!)

        val envelope = SentryEnvelope.from(fixture.serializer, createSession(), null)

        val hints = HintUtils.createWithTypeCheckHint(SessionStartHint())
        cache.store(envelope, hints)

        val currentFile = File(fixture.options.cacheDirPath!!, "$PREFIX_CURRENT_SESSION_FILE$SUFFIX_CURRENT_SESSION_FILE")
        assertTrue(currentFile.exists())

        HintUtils.setTypeCheckHint(hints, SessionEndHint())
        cache.store(envelope, hints)
        assertFalse(currentFile.exists())

        file.deleteRecursively()
    }

    @Test
    fun `updates current file on session update and read it back`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.cacheDirPath!!)

        val envelope = SentryEnvelope.from(fixture.serializer, createSession(), null)

        val hints = HintUtils.createWithTypeCheckHint(SessionStartHint())
        cache.store(envelope, hints)

        val currentFile = File(fixture.options.cacheDirPath!!, "$PREFIX_CURRENT_SESSION_FILE$SUFFIX_CURRENT_SESSION_FILE")
        assertTrue(currentFile.exists())

        val session = fixture.serializer.deserialize(currentFile.bufferedReader(Charsets.UTF_8), Session::class.java)
        assertNotNull(session)

        currentFile.delete()

        file.deleteRecursively()
    }

    @Test
    fun `when session start and current file already exist, close session and start a new one`() {
        val cache = fixture.getSUT()

        val envelope = SentryEnvelope.from(fixture.serializer, createSession(), null)

        val hints = HintUtils.createWithTypeCheckHint(SessionStartHint())
        cache.store(envelope, hints)

        val newEnvelope = SentryEnvelope.from(fixture.serializer, createSession(), null)

        cache.store(newEnvelope, hints)
        verify(fixture.logger).log(eq(SentryLevel.WARNING), eq("Current session is not ended, we'd need to end it."))
    }

    @Test
    fun `when session start, current file already exist and crash marker file exist, end session and delete marker file`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.cacheDirPath!!)
        val markerFile = File(fixture.options.cacheDirPath!!, EnvelopeCache.NATIVE_CRASH_MARKER_FILE)
        markerFile.mkdirs()
        assertTrue(markerFile.exists())

        val envelope = SentryEnvelope.from(fixture.serializer, createSession(), null)

        val hints = HintUtils.createWithTypeCheckHint(SessionStartHint())
        cache.store(envelope, hints)

        val newEnvelope = SentryEnvelope.from(fixture.serializer, createSession(), null)

        cache.store(newEnvelope, hints)
        verify(fixture.logger).log(eq(SentryLevel.INFO), eq("Crash marker file exists, last Session is gonna be Crashed."))
        assertFalse(markerFile.exists())
        file.deleteRecursively()
    }

    @Test
    fun `when session start, current file already exist and crash marker file exist, end session with given timestamp`() {
        val cache = fixture.getSUT()
        val file = File(fixture.options.cacheDirPath!!)
        val markerFile = File(fixture.options.cacheDirPath!!, EnvelopeCache.NATIVE_CRASH_MARKER_FILE)
        File(fixture.options.cacheDirPath!!, ".sentry-native").mkdirs()
        markerFile.createNewFile()
        val date = "2020-02-07T14:16:00.000Z"
        markerFile.writeText(charset = Charsets.UTF_8, text = date)
        val envelope = SentryEnvelope.from(fixture.serializer, createSession(), null)

        val hints = HintUtils.createWithTypeCheckHint(SessionStartHint())
        cache.store(envelope, hints)

        val newEnvelope = SentryEnvelope.from(fixture.serializer, createSession(), null)

        cache.store(newEnvelope, hints)
        assertFalse(markerFile.exists())
        file.deleteRecursively()
        File(fixture.options.cacheDirPath!!).deleteRecursively()
    }

    @Test
    fun `when native crash marker file exist, mark isCrashedLastRun`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.cacheDirPath!!)
        val markerFile = File(fixture.options.cacheDirPath!!, EnvelopeCache.NATIVE_CRASH_MARKER_FILE)
        markerFile.mkdirs()
        assertTrue(markerFile.exists())

        val envelope = SentryEnvelope.from(fixture.serializer, createSession(), null)

        val hints = HintUtils.createWithTypeCheckHint(SessionStartHint())
        cache.store(envelope, hints)

        val newEnvelope = SentryEnvelope.from(fixture.serializer, createSession(), null)

        // since the first store call would set as readCrashedLastRun=true
        SentryCrashLastRunState.getInstance().reset()

        cache.store(newEnvelope, hints)
        verify(fixture.logger).log(eq(SentryLevel.INFO), eq("Crash marker file exists, last Session is gonna be Crashed."))
        assertFalse(markerFile.exists())
        file.deleteRecursively()

        // passing empty string since readCrashedLastRun is already set
        assertTrue(SentryCrashLastRunState.getInstance().isCrashedLastRun("", false)!!)
    }

    @Test
    fun `when java crash marker file exist, mark isCrashedLastRun`() {
        val cache = fixture.getSUT()

        val markerFile = File(fixture.options.cacheDirPath!!, EnvelopeCache.CRASH_MARKER_FILE)
        markerFile.mkdirs()
        assertTrue(markerFile.exists())

        val envelope = SentryEnvelope.from(fixture.serializer, createSession(), null)

        val hints = HintUtils.createWithTypeCheckHint(SessionStartHint())
        cache.store(envelope, hints)

        // passing empty string since readCrashedLastRun is already set
        assertTrue(SentryCrashLastRunState.getInstance().isCrashedLastRun("", false)!!)
        assertFalse(markerFile.exists())
    }

    @Test
    fun `write java marker file to disk when uncaught exception hint`() {
        val cache = fixture.getSUT()

        val markerFile = File(fixture.options.cacheDirPath!!, EnvelopeCache.CRASH_MARKER_FILE)
        assertFalse(markerFile.exists())

        val envelope = SentryEnvelope.from(fixture.serializer, SentryEvent(), null)

        val hints = HintUtils.createWithTypeCheckHint(UncaughtExceptionHint(0, NoOpLogger.getInstance()))
        cache.store(envelope, hints)

        assertTrue(markerFile.exists())
    }

    private fun createSession(): Session {
        return Session("dis", User(), "env", "rel")
    }
}
