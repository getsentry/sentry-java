package io.sentry.cache

import io.sentry.DateUtils
import io.sentry.Hint
import io.sentry.ILogger
import io.sentry.NoOpLogger
import io.sentry.SentryCrashLastRunState
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.SentryUUID
import io.sentry.Session
import io.sentry.Session.State
import io.sentry.Session.State.Ok
import io.sentry.UncaughtExceptionHandlerIntegration.UncaughtExceptionHint
import io.sentry.cache.EnvelopeCache.PREFIX_CURRENT_SESSION_FILE
import io.sentry.cache.EnvelopeCache.SUFFIX_SESSION_FILE
import io.sentry.hints.AbnormalExit
import io.sentry.hints.SessionEndHint
import io.sentry.hints.SessionStartHint
import io.sentry.protocol.SentryId
import io.sentry.util.HintUtils
import org.mockito.kotlin.mock
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EnvelopeCacheTest {
    private class Fixture {
        val dir: Path = Files.createTempDirectory("sentry-session-cache-test")
        val options = SentryOptions()
        val logger = mock<ILogger>()

        fun getSUT(): EnvelopeCache {
            options.cacheDirPath = dir.toAbsolutePath().toFile().absolutePath

            options.setLogger(logger)
            options.setDebug(true)

            return EnvelopeCache.create(options) as EnvelopeCache
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

        cache.store(SentryEnvelope.from(fixture.options.serializer, createSession(), null))

        assertEquals(1, nofFiles())

        file.deleteRecursively()
    }

    @Test
    fun `tolerates discarding unknown envelope`() {
        val cache = fixture.getSUT()

        cache.discard(SentryEnvelope.from(fixture.options.serializer, createSession(), null))

        // no exception thrown
    }

    @Test
    fun `creates current file on session start`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.cacheDirPath!!)

        val envelope = SentryEnvelope.from(fixture.options.serializer, createSession(), null)

        val hints = HintUtils.createWithTypeCheckHint(SessionStartHint())
        cache.store(envelope, hints)

        val currentFile = File(fixture.options.cacheDirPath!!, "$PREFIX_CURRENT_SESSION_FILE$SUFFIX_SESSION_FILE")
        assertTrue(currentFile.exists())

        file.deleteRecursively()
    }

    @Test
    fun `deletes current file on session end`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.cacheDirPath!!)

        val envelope = SentryEnvelope.from(fixture.options.serializer, createSession(), null)

        val hints = HintUtils.createWithTypeCheckHint(SessionStartHint())
        cache.store(envelope, hints)

        val currentFile = File(fixture.options.cacheDirPath!!, "$PREFIX_CURRENT_SESSION_FILE$SUFFIX_SESSION_FILE")
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

        val envelope = SentryEnvelope.from(fixture.options.serializer, createSession(), null)

        val hints = HintUtils.createWithTypeCheckHint(SessionStartHint())
        cache.store(envelope, hints)

        val currentFile = File(fixture.options.cacheDirPath!!, "$PREFIX_CURRENT_SESSION_FILE$SUFFIX_SESSION_FILE")
        assertTrue(currentFile.exists())

        val session = fixture.options.serializer.deserialize(currentFile.bufferedReader(Charsets.UTF_8), Session::class.java)
        assertNotNull(session)

        currentFile.delete()

        file.deleteRecursively()
    }

    @Test
    fun `when native crash marker file exist, mark isCrashedLastRun`() {
        val cache = fixture.getSUT()

        val file = File(fixture.options.cacheDirPath!!)
        val markerFile = File(fixture.options.cacheDirPath!!, EnvelopeCache.NATIVE_CRASH_MARKER_FILE)
        markerFile.mkdirs()
        assertTrue(markerFile.exists())

        val envelope = SentryEnvelope.from(fixture.options.serializer, createSession(), null)

        val hints = HintUtils.createWithTypeCheckHint(SessionStartHint())
        cache.store(envelope, hints)

        val newEnvelope = SentryEnvelope.from(fixture.options.serializer, createSession(), null)

        // since the first store call would set as readCrashedLastRun=true
        SentryCrashLastRunState.getInstance().reset()

        cache.store(newEnvelope, hints)
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

        val envelope = SentryEnvelope.from(fixture.options.serializer, createSession(), null)

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

        val envelope = SentryEnvelope.from(fixture.options.serializer, SentryEvent(), null)

        val hints = HintUtils.createWithTypeCheckHint(UncaughtExceptionHint(0, NoOpLogger.getInstance()))
        cache.store(envelope, hints)

        assertTrue(markerFile.exists())
    }

    @Test
    fun `store with StartSession hint flushes previous session`() {
        val cache = fixture.getSUT()

        val envelope = SentryEnvelope.from(fixture.options.serializer, SentryEvent(), null)
        val hints = HintUtils.createWithTypeCheckHint(SessionStartHint())
        cache.store(envelope, hints)

        assertTrue(cache.waitPreviousSessionFlush())
    }

    @Test
    fun `SessionStart hint saves unfinished session to previous_session file`() {
        val cache = fixture.getSUT()

        val previousSessionFile = EnvelopeCache.getPreviousSessionFile(fixture.options.cacheDirPath!!)
        val currentSessionFile = EnvelopeCache.getCurrentSessionFile(fixture.options.cacheDirPath!!)
        val session = createSession()
        fixture.options.serializer.serialize(session, currentSessionFile.bufferedWriter())

        assertFalse(previousSessionFile.exists())

        val envelope = SentryEnvelope.from(fixture.options.serializer, SentryEvent(), null)
        val hints = HintUtils.createWithTypeCheckHint(SessionStartHint())
        cache.store(envelope, hints)

        assertTrue(previousSessionFile.exists())
        val persistedSession = fixture.options.serializer.deserialize(previousSessionFile.bufferedReader(), Session::class.java)
        assertEquals("dis", persistedSession!!.distinctId)
    }

    @Test
    fun `AbnormalExit hint marks previous session as abnormal with abnormal mechanism and current timestamp`() {
        val cache = fixture.getSUT()

        val previousSessionFile = EnvelopeCache.getPreviousSessionFile(fixture.options.cacheDirPath!!)
        val session = createSession()
        fixture.options.serializer.serialize(session, previousSessionFile.bufferedWriter())

        val envelope = SentryEnvelope.from(fixture.options.serializer, SentryEvent(), null)
        val abnormalHint = object : AbnormalExit {
            override fun mechanism(): String? = "abnormal_mechanism"
            override fun ignoreCurrentThread(): Boolean = false
            override fun timestamp(): Long? = null
        }
        val hints = HintUtils.createWithTypeCheckHint(abnormalHint)
        cache.store(envelope, hints)

        val updatedSession = fixture.options.serializer.deserialize(previousSessionFile.bufferedReader(), Session::class.java)
        assertEquals(State.Abnormal, updatedSession!!.status)
        assertEquals("abnormal_mechanism", updatedSession.abnormalMechanism)
        assertTrue { updatedSession.timestamp!!.time - DateUtils.getCurrentDateTime().time < 1000 }
    }

    @Test
    fun `previous session uses AbnormalExit hint timestamp when available`() {
        val cache = fixture.getSUT()

        val previousSessionFile = EnvelopeCache.getPreviousSessionFile(fixture.options.cacheDirPath!!)
        val sessionStarted = Date(2023, 10, 1)
        val sessionExitedWithAbnormal = sessionStarted.time + TimeUnit.HOURS.toMillis(3)
        val session = createSession(sessionStarted)
        fixture.options.serializer.serialize(session, previousSessionFile.bufferedWriter())

        val envelope = SentryEnvelope.from(fixture.options.serializer, SentryEvent(), null)
        val abnormalHint = object : AbnormalExit {
            override fun mechanism(): String = "abnormal_mechanism"
            override fun ignoreCurrentThread(): Boolean = false
            override fun timestamp(): Long = sessionExitedWithAbnormal
        }
        val hints = HintUtils.createWithTypeCheckHint(abnormalHint)
        cache.store(envelope, hints)

        val updatedSession = fixture.options.serializer.deserialize(previousSessionFile.bufferedReader(), Session::class.java)
        assertEquals(sessionExitedWithAbnormal, updatedSession!!.timestamp!!.time)
    }

    @Test
    fun `when AbnormalExit happened before previous session start, does not mark as abnormal`() {
        val cache = fixture.getSUT()

        val previousSessionFile = EnvelopeCache.getPreviousSessionFile(fixture.options.cacheDirPath!!)
        val sessionStarted = Date(2023, 10, 1)
        val sessionExitedWithAbnormal = sessionStarted.time - TimeUnit.HOURS.toMillis(3)
        val session = createSession(sessionStarted)
        fixture.options.serializer.serialize(session, previousSessionFile.bufferedWriter())

        val envelope = SentryEnvelope.from(fixture.options.serializer, SentryEvent(), null)
        val abnormalHint = object : AbnormalExit {
            override fun mechanism(): String = "abnormal_mechanism"
            override fun ignoreCurrentThread(): Boolean = false
            override fun timestamp(): Long = sessionExitedWithAbnormal
        }
        val hints = HintUtils.createWithTypeCheckHint(abnormalHint)
        cache.store(envelope, hints)

        val updatedSession = fixture.options.serializer.deserialize(previousSessionFile.bufferedReader(), Session::class.java)
        assertEquals(Ok, updatedSession!!.status)
        assertEquals(null, updatedSession.abnormalMechanism)
    }

    private fun createSession(started: Date? = null): Session {
        return Session(
            Ok,
            started ?: DateUtils.getCurrentDateTime(),
            DateUtils.getCurrentDateTime(),
            0,
            "dis",
            SentryUUID.generateSentryId(),
            true,
            null,
            null,
            null,
            null,
            "env",
            "rel",
            null
        )
    }

    @Test
    fun `two items with the same event id can be stored side-by-side`() {
        val cache = fixture.getSUT()

        val eventId = SentryId()

        val envelopeA = SentryEnvelope.from(
            fixture.options.serializer,
            SentryEvent().apply {
                setEventId(eventId)
            },
            null
        )

        val envelopeB = SentryEnvelope.from(
            fixture.options.serializer,
            SentryEvent().apply {
                setEventId(eventId)
            },
            null
        )

        cache.store(envelopeA, Hint())
        cache.store(envelopeB, Hint())

        assertEquals(2, cache.directory.list()?.size)
    }
}
