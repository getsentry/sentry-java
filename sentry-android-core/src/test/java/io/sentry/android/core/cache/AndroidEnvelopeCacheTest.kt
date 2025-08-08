package io.sentry.android.core.cache

import io.sentry.ISerializer
import io.sentry.NoOpLogger
import io.sentry.SentryEnvelope
import io.sentry.SentryOptions
import io.sentry.UncaughtExceptionHandlerIntegration.UncaughtExceptionHint
import io.sentry.android.core.AnrV2Integration.AnrV2Hint
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.android.core.performance.AppStartMetrics
import io.sentry.cache.EnvelopeCache
import io.sentry.transport.ICurrentDateProvider
import io.sentry.util.HintUtils
import java.io.File
import java.lang.IllegalArgumentException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.whenever

class AndroidEnvelopeCacheTest {
  @get:Rule val tmpDir = TemporaryFolder()

  private class Fixture {
    val envelope = mock<SentryEnvelope> { whenever(it.header).thenReturn(mock()) }
    val options = SentryAndroidOptions()
    val dateProvider = mock<ICurrentDateProvider>()
    lateinit var startupCrashMarkerFile: File
    lateinit var lastReportedAnrFile: File

    fun getSut(
      dir: TemporaryFolder,
      appStartMillis: Long? = null,
      currentTimeMillis: Long? = null,
      optionsCallback: ((SentryOptions) -> Unit)? = null,
    ): AndroidEnvelopeCache {
      options.cacheDirPath = dir.newFolder("sentry-cache").absolutePath
      optionsCallback?.invoke(options)
      val outboxDir = File(options.outboxPath!!)
      outboxDir.mkdirs()

      startupCrashMarkerFile = File(outboxDir, EnvelopeCache.STARTUP_CRASH_MARKER_FILE)
      lastReportedAnrFile = File(options.cacheDirPath!!, AndroidEnvelopeCache.LAST_ANR_REPORT)

      if (appStartMillis != null) {
        AppStartMetrics.getInstance().apply {
          if (options.isEnablePerformanceV2) {
            appStartTimeSpan.setStartedAt(appStartMillis)
            sdkInitTimeSpan.setStartedAt(appStartMillis)
          } else {
            sdkInitTimeSpan.setStartedAt(appStartMillis)
          }
        }
      }
      if (currentTimeMillis != null) {
        whenever(dateProvider.currentTimeMillis).thenReturn(currentTimeMillis)
      }

      return AndroidEnvelopeCache(options, dateProvider)
    }
  }

  private val fixture = Fixture()

  @BeforeTest
  fun `set up`() {
    AppStartMetrics.getInstance().clear()
  }

  @Test
  fun `when no uncaught hint exists, does not write startup crash file`() {
    val cache = fixture.getSut(tmpDir)

    cache.store(fixture.envelope)

    assertFalse(fixture.startupCrashMarkerFile.exists())
  }

  @Test
  fun `when startup time is null, does not write startup crash file`() {
    val cache = fixture.getSut(tmpDir)

    val hints = HintUtils.createWithTypeCheckHint(UncaughtHint())
    cache.storeEnvelope(fixture.envelope, hints)

    assertFalse(fixture.startupCrashMarkerFile.exists())
  }

  @Test
  fun `when time since sdk init is more than duration threshold, does not write startup crash file`() {
    val cache = fixture.getSut(dir = tmpDir, appStartMillis = 1000L, currentTimeMillis = 5000L)

    val hints = HintUtils.createWithTypeCheckHint(UncaughtHint())
    cache.storeEnvelope(fixture.envelope, hints)

    assertFalse(fixture.startupCrashMarkerFile.exists())
  }

  @Test
  fun `when outbox dir is not set, does not write startup crash file`() {
    val cache = fixture.getSut(dir = tmpDir, appStartMillis = 1000L, currentTimeMillis = 2000L)

    fixture.options.cacheDirPath = null

    val hints = HintUtils.createWithTypeCheckHint(UncaughtHint())
    cache.storeEnvelope(fixture.envelope, hints)

    assertFalse(fixture.startupCrashMarkerFile.exists())
  }

  @Test
  fun `when time since sdk init is less than duration threshold, writes startup crash file`() {
    val cache = fixture.getSut(dir = tmpDir, appStartMillis = 1000L, currentTimeMillis = 2000L)

    val hints = HintUtils.createWithTypeCheckHint(UncaughtHint())
    cache.storeEnvelope(fixture.envelope, hints)

    assertTrue(fixture.startupCrashMarkerFile.exists())
  }

  @Test
  fun `when no AnrV2 hint exists, does not write last anr report file`() {
    val cache = fixture.getSut(tmpDir)

    cache.store(fixture.envelope)

    assertFalse(fixture.lastReportedAnrFile.exists())
  }

  @Test
  fun `when cache dir is not set, does not write last anr report file`() {
    val cache = fixture.getSut(tmpDir)

    fixture.options.cacheDirPath = null

    val hints =
      HintUtils.createWithTypeCheckHint(
        AnrV2Hint(0, NoOpLogger.getInstance(), 12345678L, false, false)
      )
    cache.storeEnvelope(fixture.envelope, hints)

    assertFalse(fixture.lastReportedAnrFile.exists())
  }

  @Test
  fun `when AnrV2 hint exists, writes last anr report timestamp into file`() {
    val cache = fixture.getSut(tmpDir)

    val hints =
      HintUtils.createWithTypeCheckHint(
        AnrV2Hint(0, NoOpLogger.getInstance(), 12345678L, false, false)
      )
    cache.storeEnvelope(fixture.envelope, hints)

    assertTrue(fixture.lastReportedAnrFile.exists())
    assertEquals("12345678", fixture.lastReportedAnrFile.readText())
  }

  @Test
  fun `when cache dir is not set, throws upon reading last reported anr file`() {
    fixture.getSut(tmpDir)

    fixture.options.cacheDirPath = null

    try {
      AndroidEnvelopeCache.lastReportedAnr(fixture.options)
    } catch (e: Throwable) {
      assertTrue { e is IllegalArgumentException }
    }
  }

  @Test
  fun `when last reported anr file does not exist, returns null upon reading`() {
    fixture.getSut(tmpDir)

    val lastReportedAnr = AndroidEnvelopeCache.lastReportedAnr(fixture.options)

    assertEquals(null, lastReportedAnr)
  }

  @Test
  fun `when last reported anr file exists, returns timestamp from the file upon reading`() {
    fixture.getSut(tmpDir)
    fixture.lastReportedAnrFile.writeText("87654321")

    val lastReportedAnr = AndroidEnvelopeCache.lastReportedAnr(fixture.options)

    assertEquals(87654321L, lastReportedAnr)
  }

  @Test
  fun `returns false if storing fails`() {
    val serializer = mock<ISerializer>()
    val cache = fixture.getSut(tmpDir) { options -> options.setSerializer(serializer) }
    whenever(serializer.serialize(same(fixture.envelope), any()))
      .thenThrow(RuntimeException("forced ex"))
    val hints = HintUtils.createWithTypeCheckHint(UncaughtHint())

    val didStore = cache.storeEnvelope(fixture.envelope, hints)
    assertFalse(didStore)
  }

  internal class UncaughtHint : UncaughtExceptionHint(0, NoOpLogger.getInstance())
}
