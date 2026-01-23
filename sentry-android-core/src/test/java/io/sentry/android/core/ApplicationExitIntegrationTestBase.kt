package io.sentry.android.core

import android.app.ActivityManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.sentry.Hint
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.Integration
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.SentryLevel
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
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.atMost
import org.mockito.kotlin.check
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowActivityManager

abstract class ApplicationExitIntegrationTestBase<THint : Any> {

  protected abstract val config: IntegrationTestConfig<THint>

  @get:Rule val tmpDir = TemporaryFolder()

  protected val fixture: ApplicationExitTestFixture<THint> by lazy {
    ApplicationExitTestFixture(config)
  }
  protected val oldTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10)
  protected val newTimestamp = oldTimestamp + TimeUnit.DAYS.toMillis(5)

  @BeforeTest
  fun `set up`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    // the integration test app has no native library and as such we have to inject one here
    context.applicationInfo.nativeLibraryDir =
      "/data/app/~~YtXYvdWm5vDHUWYCmVLG_Q==/io.sentry.samples.android-Q2_nG8SyOi4X_6hGGDGE2Q==/lib/arm64"
    fixture.init(context)
  }

  @Test
  fun `when cacheDir is not set, does not process historical exits`() {
    val integration = fixture.getSut(null, useImmediateExecutorService = false)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.options.executorService, never()).submit(any())
  }

  @Test
  fun `when integration is not enabled, does not process historical exits`() {
    val integration = fixture.getSut(tmpDir, enabled = false, useImmediateExecutorService = false)

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
  fun `when there are no matching exits, does not capture events`() {
    val integration = fixture.getSut(tmpDir)
    fixture.addAppExitInfo(reason = null)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
  }

  @Test
  fun `when latest exit is older than 90 days, does not capture events`() {
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
  fun `when latest exit has already been reported, does not capture events`() {
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp)
    fixture.addAppExitInfo(timestamp = oldTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
  }

  @Test
  fun `when no exits have ever been reported, captures events`() {
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = null)
    fixture.addAppExitInfo(timestamp = oldTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes).captureEvent(any(), anyOrNull<Hint>())
  }

  @Test
  fun `when latest exit has not been reported, captures event with enriching`() {
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp)
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes)
      .captureEvent(
        check { event ->
          assertEquals(event.timestamp!!.time, newTimestamp)
          assertEquals(event.level, SentryLevel.FATAL)
          assertEnrichedEvent(event)
        },
        argThat<Hint> {
          val hint = config.hintAccessors.cast(HintUtils.getSentrySdkHint(this))
          config.hintAccessors.shouldEnrich(hint)
        },
      )
  }

  @Test
  fun `waits for events to be flushed on disk`() {
    val integration =
      fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp, flushTimeoutMillis = 500L)
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
    verify(fixture.logger, never())
      .log(any(), argThat { startsWith(config.flushLogPrefix) }, any<Any>())
  }

  @Test
  fun `when latest event was dropped, does not block flushing`() {
    val integration =
      fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp, lastEventId = SentryId.EMPTY_ID)
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes).captureEvent(any(), anyOrNull<Hint>())
    verify(fixture.logger, never())
      .log(any(), argThat { startsWith(config.flushLogPrefix) }, any<Any>())
  }

  @Test
  fun `historical exits are reported non-enriched`() {
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp)
    fixture.addAppExitInfo(timestamp = newTimestamp - 2 * 60 * 1000)
    fixture.addAppExitInfo(timestamp = newTimestamp - 1 * 60 * 1000)
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, times(2))
      .captureEvent(
        any(),
        argThat<Hint> {
          val hint = config.hintAccessors.cast(HintUtils.getSentrySdkHint(this))
          !config.hintAccessors.shouldEnrich(hint)
        },
      )
  }

  @Test
  fun `when historical flag is disabled, does not report`() {
    val integration =
      fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp, reportHistorical = false)
    fixture.addAppExitInfo(timestamp = newTimestamp - 2 * 60 * 1000)
    fixture.addAppExitInfo(timestamp = newTimestamp - 1 * 60 * 1000)
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, atMost(1))
      .captureEvent(
        any(),
        argThat<Hint> {
          val hint = config.hintAccessors.cast(HintUtils.getSentrySdkHint(this))
          config.hintAccessors.shouldEnrich(hint)
        },
      )
  }

  @Test
  fun `historical exits are reported in reverse order to keep track of last reported exit in a marker file`() {
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp)
    fixture.addAppExitInfo(timestamp = newTimestamp - TimeUnit.DAYS.toMillis(2))
    fixture.addAppExitInfo(timestamp = newTimestamp - TimeUnit.DAYS.toMillis(1))
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

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
  fun `timestamp is passed with the hint`() {
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp)
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes)
      .captureEvent(
        any(),
        argThat<Hint> {
          val hint = config.hintAccessors.cast(HintUtils.getSentrySdkHint(this))
          config.hintAccessors.timestamp(hint) == newTimestamp
        },
      )
  }

  @Test
  fun `awaits for previous session flush if cache is EnvelopeCache`() {
    val integration =
      fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp, sessionFlushTimeoutMillis = 500L)
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
        lastReportedTimestamp = oldTimestamp,
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
      fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp, sessionFlushTimeoutMillis = 500L)
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.logger)
      .log(
        any(),
        argThat { startsWith("Timed out waiting to flush previous session to its own file.") },
        any<Any>(),
      )
    assertTrue((fixture.options.envelopeDiskCache as EnvelopeCache).waitPreviousSessionFlush())
  }

  @Test
  fun `when traceInputStream is null, does not report`() {
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp)
    fixture.addAppExitInfo(timestamp = newTimestamp, addTrace = false)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
  }

  @Test
  fun `when traceInputStream has bad data, does not report`() {
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp)
    fixture.addAppExitInfo(timestamp = newTimestamp, addBadTrace = true)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
  }

  protected open fun assertEnrichedEvent(event: SentryEvent) {}

  protected data class HintAccessors<THint : Any>(
    val cast: (Any?) -> THint,
    val shouldEnrich: (THint) -> Boolean,
    val timestamp: (THint) -> Long,
  )

  protected data class IntegrationTestConfig<THint : Any>(
    val setEnabledFlag: SentryAndroidOptions.(Boolean) -> Unit,
    val setReportHistoricalFlag: SentryAndroidOptions.(Boolean) -> Unit,
    val createIntegration: (Context) -> Integration,
    val lastReportedFileName: String,
    val defaultExitReason: Int,
    val hintAccessors: HintAccessors<THint>,
    val addExitInfo:
      ApplicationExitTestFixture<THint>.(
        reason: Int?, timestamp: Long?, importance: Int?, addTrace: Boolean, addBadTrace: Boolean,
      ) -> Unit,
    val flushLogPrefix: String,
  )

  protected class ApplicationExitTestFixture<THint : Any>(
    private val config: IntegrationTestConfig<THint>
  ) {
    lateinit var context: Context
    lateinit var shadowActivityManager: ShadowActivityManager
    lateinit var lastReportedFile: File

    val options = SentryAndroidOptions()
    val scopes = mock<IScopes>()
    val logger = mock<ILogger>()

    fun init(appContext: Context) {
      context = appContext
      val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
      shadowActivityManager = Shadow.extract(activityManager)
    }

    fun getSut(
      dir: TemporaryFolder?,
      useImmediateExecutorService: Boolean = true,
      enabled: Boolean = true,
      flushTimeoutMillis: Long = 0L,
      sessionFlushTimeoutMillis: Long = 0L,
      lastReportedTimestamp: Long? = null,
      lastEventId: SentryId = SentryId(),
      sessionTrackingEnabled: Boolean = true,
      reportHistorical: Boolean = true,
      extraOptions: (SentryAndroidOptions) -> Unit = {},
    ): Integration {
      options.run {
        setLogger(this@ApplicationExitTestFixture.logger)
        isDebug = true
        cacheDirPath = dir?.newFolder()?.absolutePath
        executorService = if (useImmediateExecutorService) ImmediateExecutorService() else mock()
        config.setEnabledFlag(this, enabled)
        this.flushTimeoutMillis = flushTimeoutMillis
        this.sessionFlushTimeoutMillis = sessionFlushTimeoutMillis
        this.isEnableAutoSessionTracking = sessionTrackingEnabled
        config.setReportHistoricalFlag(this, reportHistorical)
        addInAppInclude("io.sentry.samples")
        setEnvelopeDiskCache(EnvelopeCache.create(this))
        extraOptions(this)
      }
      options.cacheDirPath?.let { cacheDir ->
        lastReportedFile = File(cacheDir, config.lastReportedFileName)
        lastReportedFile.writeText(lastReportedTimestamp.toString())
      }
      whenever(scopes.captureEvent(any(), anyOrNull<Hint>())).thenReturn(lastEventId)
      return config.createIntegration(context)
    }

    fun addAppExitInfo(
      reason: Int? = config.defaultExitReason,
      timestamp: Long? = null,
      importance: Int? = null,
      addTrace: Boolean = true,
      addBadTrace: Boolean = false,
    ) {
      config.addExitInfo(this, reason, timestamp, importance, addTrace, addBadTrace)
    }
  }
}
