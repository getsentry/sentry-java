package io.sentry.android.core

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Hint
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.SentryEnvelope
import io.sentry.SentryLevel
import io.sentry.android.core.TombstoneIntegration.TombstoneHint
import io.sentry.android.core.cache.AndroidEnvelopeCache
import io.sentry.cache.EnvelopeCache
import io.sentry.hints.DiskFlushNotification
import io.sentry.hints.SessionStartHint
import io.sentry.protocol.SentryId
import io.sentry.test.ImmediateExecutorService
import io.sentry.util.HintUtils
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.atMost
import org.mockito.kotlin.check
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowActivityManager
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class TombstoneIntegrationTest {
  @get:Rule val tmpDir = TemporaryFolder()

  class Fixture {
    lateinit var context: Context
    lateinit var shadowActivityManager: ShadowActivityManager
    lateinit var lastReportedTombstoneFile: File

    val options = SentryAndroidOptions()
    val scopes = mock<IScopes>()
    val logger = mock<ILogger>()

    fun getSut(
      dir: TemporaryFolder?,
      useImmediateExecutorService: Boolean = true,
      isTombstoneEnabled: Boolean = true,
      flushTimeoutMillis: Long = 0L,
      sessionFlushTimeoutMillis: Long = 0L,
      lastReportedTombstoneTimestamp: Long? = null,
      lastEventId: SentryId = SentryId(),
      sessionTrackingEnabled: Boolean = true,
      reportHistoricalTombstones: Boolean = true,
    ): TombstoneIntegration {
      options.run {
        setLogger(this@Fixture.logger)
        isDebug = true
        cacheDirPath = dir?.newFolder()?.absolutePath
        executorService = if (useImmediateExecutorService) ImmediateExecutorService() else mock()
        this.isTombstoneEnabled = isTombstoneEnabled
        this.flushTimeoutMillis = flushTimeoutMillis
        this.sessionFlushTimeoutMillis = sessionFlushTimeoutMillis
        this.isEnableAutoSessionTracking = sessionTrackingEnabled
        this.isReportHistoricalTombstones = reportHistoricalTombstones
        addInAppInclude("io.sentry.samples")
        setEnvelopeDiskCache(EnvelopeCache.create(this))
      }
      options.cacheDirPath?.let { cacheDir ->
        lastReportedTombstoneFile = File(cacheDir, AndroidEnvelopeCache.LAST_TOMBSTONE_REPORT)
        lastReportedTombstoneFile.writeText(lastReportedTombstoneTimestamp.toString())
      }
      whenever(scopes.captureEvent(any(), anyOrNull<Hint>())).thenReturn(lastEventId)
      return TombstoneIntegration(context)
    }

    fun addAppExitInfo(
      reason: Int? = ApplicationExitInfo.REASON_CRASH_NATIVE,
      timestamp: Long? = null,
      importance: Int? = null,
      addTrace: Boolean = true,
      addBadTrace: Boolean = false,
    ) {
      val builder = ApplicationExitInfoBuilder.newBuilder()
      if (reason != null) {
        builder.setReason(reason)
      }
      if (timestamp != null) {
        builder.setTimestamp(timestamp)
      }
      if (importance != null) {
        builder.setImportance(importance)
      }
      val exitInfo =
        spy(builder.build()) {
          if (!addTrace) {
            return
          }
          if (addBadTrace) {
            whenever(mock.traceInputStream).thenReturn("XXXXX".byteInputStream())
          } else {
            whenever(mock.traceInputStream)
              .thenReturn(File("src/test/resources/tombstone.pb").inputStream())
          }
        }
      shadowActivityManager.addApplicationExitInfo(exitInfo)
    }
  }

  private val fixture = Fixture()
  private val oldTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10)
  private val newTimestamp = oldTimestamp + TimeUnit.DAYS.toMillis(5)

  @BeforeTest
  fun `set up`() {
    fixture.context = ApplicationProvider.getApplicationContext()
    val activityManager =
      fixture.context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
    fixture.shadowActivityManager = Shadow.extract(activityManager)
  }

  @Test
  fun `when cacheDir is not set, does not process historical exits`() {
    val integration = fixture.getSut(null, useImmediateExecutorService = false)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.options.executorService, never()).submit(any())
  }

  @Test
  fun `when tombstone tracking is not enabled, does not process historical exits`() {
    val integration =
      fixture.getSut(tmpDir, isTombstoneEnabled = false, useImmediateExecutorService = false)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.options.executorService, never()).submit(any())
  }

  @Test
  fun `when historical exit list is empty, does not process historical exits`() {
    val integration = fixture.getSut(tmpDir)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
  }

  @Test
  fun `when there are no tombstones in historical exits, does not capture events`() {
    val integration = fixture.getSut(tmpDir)
    fixture.addAppExitInfo(reason = null)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
  }

  @Test
  fun `when latest Tombstone is older than 90 days, does not capture events`() {
    val oldTimestamp =
      System.currentTimeMillis() -
        ApplicationExitInfoHistoryDispatcher.NINETY_DAYS_THRESHOLD -
        TimeUnit.DAYS.toMillis(2)
    val integration = fixture.getSut(tmpDir)
    fixture.addAppExitInfo(timestamp = oldTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
  }

  @Test
  fun `when latest Tombstone has already been reported, does not capture events`() {
    val integration = fixture.getSut(tmpDir, lastReportedTombstoneTimestamp = oldTimestamp)
    fixture.addAppExitInfo(timestamp = oldTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
  }

  @Test
  fun `when no Tombstones have ever been reported, captures events`() {
    val integration = fixture.getSut(tmpDir, lastReportedTombstoneTimestamp = null)
    fixture.addAppExitInfo(timestamp = oldTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes).captureEvent(any(), anyOrNull<Hint>())
  }

  @Test
  fun `when latest Tombstone has not been reported, captures event with enriching`() {
    val integration = fixture.getSut(tmpDir, lastReportedTombstoneTimestamp = oldTimestamp)
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes)
      .captureEvent(
        check {
          assertEquals(newTimestamp, it.timestamp.time)
          assertEquals(SentryLevel.FATAL, it.level)
          assertEquals("native", it.platform)

          val crashedThreadId = 21891.toLong()
          assertEquals(crashedThreadId, it.exceptions!![0].threadId)
          val crashedThread = it.threads!!.find { thread -> thread.id == crashedThreadId }
          assertEquals("samples.android", crashedThread!!.name)
          assertTrue(crashedThread.isCrashed!!)

          val image =
            it.debugMeta?.images?.find { image ->
              image.debugId == "f60b4b74005f33fb3ef3b98aa4546008"
            }
          assertNotNull(image)
          assertEquals("/system/lib64/libcompiler_rt.so", image.codeFile)
          assertEquals("0x764c32a000", image.imageAddr)
          assertEquals(32768, image.imageSize)
        },
        argThat<Hint> {
          val hint = HintUtils.getSentrySdkHint(this)
          (hint as TombstoneHint).shouldEnrich()
        },
      )
  }

  @Test
  fun `waits for Tombstone events to be flushed on disk`() {
    val integration =
      fixture.getSut(
        tmpDir,
        lastReportedTombstoneTimestamp = oldTimestamp,
        flushTimeoutMillis = 500L,
      )
    fixture.addAppExitInfo(timestamp = newTimestamp)

    whenever(fixture.scopes.captureEvent(any(), any<Hint>())).thenAnswer { invocation ->
      val hint = HintUtils.getSentrySdkHint(invocation.getArgument(1)) as DiskFlushNotification
      thread {
        Thread.sleep(200L)
        hint.markFlushed()
      }
      SentryId()
    }

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes).captureEvent(any(), anyOrNull<Hint>())
    // shouldn't fall into timed out state, because we marked event as flushed on another thread
    verify(fixture.logger, never())
      .log(
        any(),
        argThat { startsWith("Timed out waiting to flush Tombstone event to disk.") },
        any<Any>(),
      )
  }

  @Test
  fun `when latest Tombstone event was dropped, does not block flushing`() {
    val integration =
      fixture.getSut(
        tmpDir,
        lastReportedTombstoneTimestamp = oldTimestamp,
        lastEventId = SentryId.EMPTY_ID,
      )
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes).captureEvent(any(), anyOrNull<Hint>())
    // we do not call markFlushed, hence it should time out waiting for flush, but because
    // we drop the event, it should not even come to this if-check
    verify(fixture.logger, never())
      .log(
        any(),
        argThat { startsWith("Timed out waiting to flush Tombstone event to disk.") },
        any<Any>(),
      )
  }

  @Test
  fun `historical Tombstones are reported non-enriched`() {
    val integration = fixture.getSut(tmpDir, lastReportedTombstoneTimestamp = oldTimestamp)
    fixture.addAppExitInfo(timestamp = newTimestamp - 2 * 60 * 1000)
    fixture.addAppExitInfo(timestamp = newTimestamp - 1 * 60 * 1000)
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, times(2))
      .captureEvent(
        any(),
        argThat<Hint> {
          val hint = HintUtils.getSentrySdkHint(this)
          !(hint as TombstoneHint).shouldEnrich()
        },
      )
  }

  @Test
  fun `when historical Tombstones flag is disabled, does not report`() {
    val integration =
      fixture.getSut(
        tmpDir,
        lastReportedTombstoneTimestamp = oldTimestamp,
        reportHistoricalTombstones = false,
      )
    fixture.addAppExitInfo(timestamp = newTimestamp - 2 * 60 * 1000)
    fixture.addAppExitInfo(timestamp = newTimestamp - 1 * 60 * 1000)
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    // only the latest tombstone is reported which should be enrichable
    verify(fixture.scopes, atMost(1))
      .captureEvent(
        any(),
        argThat<Hint> {
          val hint = HintUtils.getSentrySdkHint(this)
          (hint as TombstoneHint).shouldEnrich()
        },
      )
  }

  @Test
  fun `historical Tombstones are reported in reverse order to keep track of last reported Tombstone in a marker file`() {
    val integration = fixture.getSut(tmpDir, lastReportedTombstoneTimestamp = oldTimestamp)
    // robolectric uses addFirst when adding exit infos, so the last one here will be the first on
    // the list
    fixture.addAppExitInfo(timestamp = newTimestamp - TimeUnit.DAYS.toMillis(2))
    fixture.addAppExitInfo(timestamp = newTimestamp - TimeUnit.DAYS.toMillis(1))
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    // the order is reverse here, so the oldest Tombstone will be reported first to keep track of
    // last reported Tombstone in a marker file
    inOrder(fixture.scopes) {
      verify(fixture.scopes)
        .captureEvent(
          argThat { timestamp.time == newTimestamp - TimeUnit.DAYS.toMillis(2) },
          anyOrNull<Hint>(),
        )
      verify(fixture.scopes)
        .captureEvent(
          argThat { timestamp.time == newTimestamp - TimeUnit.DAYS.toMillis(1) },
          anyOrNull<Hint>(),
        )
      verify(fixture.scopes)
        .captureEvent(argThat { timestamp.time == newTimestamp }, anyOrNull<Hint>())
    }
  }

  @Test
  fun `Tombstone timestamp is passed with the hint`() {
    val integration = fixture.getSut(tmpDir, lastReportedTombstoneTimestamp = oldTimestamp)
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes)
      .captureEvent(
        any(),
        argThat<Hint> {
          val hint = HintUtils.getSentrySdkHint(this)
          (hint as TombstoneHint).timestamp() == newTimestamp
        },
      )
  }

  @Test
  fun `awaits for previous session flush if cache is EnvelopeCache`() {
    val integration =
      fixture.getSut(
        tmpDir,
        lastReportedTombstoneTimestamp = oldTimestamp,
        sessionFlushTimeoutMillis = 500L,
      )
    fixture.addAppExitInfo(timestamp = newTimestamp)

    thread {
      Thread.sleep(200L)
      val sessionHint = HintUtils.createWithTypeCheckHint(SessionStartHint())
      fixture.options.envelopeDiskCache.storeEnvelope(
        SentryEnvelope(SentryId.EMPTY_ID, null, emptyList()),
        sessionHint,
      )
    }

    integration.register(fixture.scopes, fixture.options)

    // we store envelope with StartSessionHint on different thread after some delay, which
    // triggers the previous session flush, so no timeout
    verify(fixture.logger, never())
      .log(
        any(),
        argThat { startsWith("Timed out waiting to flush previous session to its own file.") },
        any<Any>(),
      )
  }

  @Test
  fun `does not await for previous session flush, if session tracking is disabled`() {
    val integration =
      fixture.getSut(
        tmpDir,
        lastReportedTombstoneTimestamp = oldTimestamp,
        sessionFlushTimeoutMillis = 500L,
        sessionTrackingEnabled = false,
      )
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.logger, never())
      .log(
        any(),
        argThat { startsWith("Timed out waiting to flush previous session to its own file.") },
        any<Any>(),
      )
    verify(fixture.scopes).captureEvent(any(), any<Hint>())
  }

  @Test
  fun `flushes previous session latch, if timed out waiting`() {
    val integration =
      fixture.getSut(
        tmpDir,
        lastReportedTombstoneTimestamp = oldTimestamp,
        sessionFlushTimeoutMillis = 500L,
      )
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.logger)
      .log(
        any(),
        argThat { startsWith("Timed out waiting to flush previous session to its own file.") },
        any<Any>(),
      )
    // should return true, because latch is 0 now
    assertTrue((fixture.options.envelopeDiskCache as EnvelopeCache).waitPreviousSessionFlush())
  }

  @Test
  fun `when traceInputStream is null, does not report Tombstone`() {
    val integration = fixture.getSut(tmpDir, lastReportedTombstoneTimestamp = oldTimestamp)
    fixture.addAppExitInfo(timestamp = newTimestamp, addTrace = false)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
  }

  @Test
  fun `when traceInputStream has bad data, does not report Tombstone`() {
    val integration = fixture.getSut(tmpDir, lastReportedTombstoneTimestamp = oldTimestamp)
    fixture.addAppExitInfo(timestamp = newTimestamp, addBadTrace = true)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
  }
}
