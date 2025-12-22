package io.sentry

import io.sentry.Session.State.Crashed
import io.sentry.cache.EnvelopeCache
import java.io.File
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class PreviousSessionFinalizerTest {
  @get:Rule val tmpDir = TemporaryFolder()

  @BeforeTest
  fun `set up`() {
    SentryCrashLastRunState.getInstance().reset()
  }

  @AfterTest
  fun `tear down`() {
    SentryCrashLastRunState.getInstance().reset()
  }

  class Fixture {
    val options = SentryOptions()
    val scopes = mock<IScopes>()
    val logger = mock<ILogger>()
    lateinit var sessionFile: File

    internal fun getSut(
      dir: TemporaryFolder?,
      flushTimeoutMillis: Long = 0L,
      sessionFileExists: Boolean = true,
      session: Session? = null,
      nativeCrashTimestamp: Date? = null,
      sessionTrackingEnabled: Boolean = true,
      shouldAwait: Boolean = false,
    ): PreviousSessionFinalizer {
      options.run {
        setLogger(this@Fixture.logger)
        isDebug = true
        cacheDirPath = dir?.newFolder()?.absolutePath
        this.flushTimeoutMillis = flushTimeoutMillis
        this.sessionFlushTimeoutMillis = flushTimeoutMillis
        isEnableAutoSessionTracking = sessionTrackingEnabled
        setEnvelopeDiskCache(EnvelopeCache.create(this))
        if (!shouldAwait) {
          (envelopeDiskCache as? EnvelopeCache)?.flushPreviousSession()
        }
      }
      options.cacheDirPath?.let {
        sessionFile = EnvelopeCache.getPreviousSessionFile(it)
        sessionFile.parentFile.mkdirs()
        if (sessionFileExists) {
          sessionFile.createNewFile()
        }
        if (session != null) {
          options.serializer.serialize(session, sessionFile.bufferedWriter())
        }
        if (nativeCrashTimestamp != null) {
          val nativeCrashMarker = File(it, EnvelopeCache.NATIVE_CRASH_MARKER_FILE)
          nativeCrashMarker.parentFile.mkdirs()
          nativeCrashMarker.writeText(nativeCrashTimestamp.toString())
        }
      }
      return PreviousSessionFinalizer(options, scopes)
    }

    fun sessionFromEnvelope(envelope: SentryEnvelope): Session {
      val sessionItem = envelope.items.find { it.header.type == SentryItemType.Session }
      return options.serializer.deserialize(
        sessionItem!!.data.inputStream().bufferedReader(),
        Session::class.java,
      )!!
    }
  }

  private val fixture = Fixture()

  @Test
  fun `if cacheDir is not set, does not send session update`() {
    val finalizer = fixture.getSut(null)
    finalizer.run()

    verify(fixture.scopes, never()).captureEnvelope(any())
  }

  @Test
  fun `if previous session file does not exist, does not send session update`() {
    val finalizer = fixture.getSut(tmpDir, sessionFileExists = false)
    finalizer.run()

    verify(fixture.scopes, never()).captureEnvelope(any())
  }

  @Test
  fun `if previous session file exists, but session is null, does not send session update`() {
    val finalizer = fixture.getSut(tmpDir, sessionFileExists = true, session = null)
    finalizer.run()

    verify(fixture.scopes, never()).captureEnvelope(any())
  }

  @Test
  fun `if previous session exists, sends session update with current end time`() {
    val finalizer =
      fixture.getSut(tmpDir, session = Session(null, null, null, "io.sentry.sample@1.0"))
    finalizer.run()

    verify(fixture.scopes)
      .captureEnvelope(
        argThat {
          val session = fixture.sessionFromEnvelope(this)
          session.release == "io.sentry.sample@1.0" &&
            session.timestamp!!.time - DateUtils.getCurrentDateTime().time < 1000
        }
      )
  }

  @Test
  fun `if previous session exists with abnormal mechanism, sends session update without changing end timestamp`() {
    val abnormalEndDate = Date(2023, 10, 1)
    val finalizer =
      fixture.getSut(
        tmpDir,
        session =
          Session(null, null, null, "io.sentry.sample@1.0").apply {
            update(null, null, false, "mechanism")
            end(abnormalEndDate)
          },
      )
    finalizer.run()

    verify(fixture.scopes)
      .captureEnvelope(
        argThat {
          val session = fixture.sessionFromEnvelope(this)
          session.release == "io.sentry.sample@1.0" && session.timestamp!! == abnormalEndDate
        }
      )
  }

  @Test
  fun `if native crash marker exists, marks previous session as crashed`() {
    val finalizer =
      fixture.getSut(
        tmpDir,
        session = Session(null, null, null, "io.sentry.sample@1.0"),
        nativeCrashTimestamp = Date(2023, 10, 1),
      )
    finalizer.run()

    verify(fixture.scopes)
      .captureEnvelope(
        argThat {
          val session = fixture.sessionFromEnvelope(this)
          session.release == "io.sentry.sample@1.0" && session.status == Crashed
        }
      )
  }

  @Test
  fun `if previous session file exists, deletes previous session file`() {
    val finalizer = fixture.getSut(tmpDir, sessionFileExists = true)
    finalizer.run()

    verify(fixture.scopes, never()).captureEnvelope(any())
    assertFalse(fixture.sessionFile.exists())
  }

  @Test
  fun `if session tracking is disabled, does not wait for previous session flush`() {
    val finalizer =
      fixture.getSut(
        tmpDir,
        flushTimeoutMillis = 500L,
        sessionTrackingEnabled = false,
        shouldAwait = true,
      )
    finalizer.run()

    verify(fixture.logger, never())
      .log(
        any(),
        argThat {
          startsWith(
            "Timed out waiting to flush previous session to its own file in session finalizer."
          )
        },
        any<Any>(),
      )
    verify(fixture.scopes, never()).captureEnvelope(any())
  }

  @Test
  fun `awaits for previous session flush`() {
    val finalizer = fixture.getSut(tmpDir, flushTimeoutMillis = 500L, shouldAwait = true)
    finalizer.run()

    verify(fixture.logger)
      .log(
        any(),
        argThat {
          startsWith(
            "Timed out waiting to flush previous session to its own file in session finalizer."
          )
        },
        any<Any>(),
      )
    verify(fixture.scopes, never()).captureEnvelope(any())
  }

  @Test
  fun `when previous session is already crashed, sets crashedLastRun to true`() {
    // Create a session that is already in Crashed state (simulating tombstone integration)
    val crashedSession =
      Session(null, null, null, "io.sentry.sample@1.0").apply { update(Crashed, null, true) }

    val finalizer = fixture.getSut(tmpDir, session = crashedSession)

    // crashedLastRun should not be set before running the finalizer
    assertNull(SentryCrashLastRunState.getInstance().isCrashedLastRun(null, false))

    finalizer.run()

    // crashedLastRun should be set to true after running the finalizer
    assertTrue(SentryCrashLastRunState.getInstance().isCrashedLastRun(null, false)!!)
  }

  @Test
  fun `when native crash marker exists but session is not crashed, does not set crashedLastRun`() {
    // Session is not crashed, but native crash marker exists
    val finalizer =
      fixture.getSut(
        tmpDir,
        session = Session(null, null, null, "io.sentry.sample@1.0"),
        nativeCrashTimestamp = Date(2023, 10, 1),
      )

    // crashedLastRun should not be set before running the finalizer
    assertNull(SentryCrashLastRunState.getInstance().isCrashedLastRun(null, false))

    finalizer.run()

    // crashedLastRun should NOT be set by PreviousSessionFinalizer for native crash marker case
    // (it's handled by EnvelopeCache at session start instead)
    assertNull(SentryCrashLastRunState.getInstance().isCrashedLastRun(null, false))
  }

  @Test
  fun `when previous session is already crashed and native crash marker exists, sets crashedLastRun and deletes marker`() {
    // Session is already crashed (tombstone case) AND native crash marker exists
    val crashedSession =
      Session(null, null, null, "io.sentry.sample@1.0").apply { update(Crashed, null, true) }

    val finalizer =
      fixture.getSut(tmpDir, session = crashedSession, nativeCrashTimestamp = Date(2023, 10, 1))

    val nativeCrashMarker =
      File(fixture.options.cacheDirPath!!, EnvelopeCache.NATIVE_CRASH_MARKER_FILE)
    assertTrue(nativeCrashMarker.exists())

    finalizer.run()

    // crashedLastRun should be set to true
    assertTrue(SentryCrashLastRunState.getInstance().isCrashedLastRun(null, false)!!)
    // Native crash marker should be deleted
    assertFalse(nativeCrashMarker.exists())
  }
}
