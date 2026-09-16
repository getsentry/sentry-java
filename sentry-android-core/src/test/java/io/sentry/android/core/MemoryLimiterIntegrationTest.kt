package io.sentry.android.core

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Hint
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.android.core.MemoryLimiterIntegration.MemoryLimiterHint
import io.sentry.android.core.cache.AndroidEnvelopeCache
import io.sentry.cache.EnvelopeCache
import io.sentry.protocol.SentryId
import io.sentry.test.ImmediateExecutorService
import io.sentry.transport.CurrentDateProvider
import io.sentry.util.HintUtils
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowActivityManager
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class MemoryLimiterIntegrationTest {

  @get:Rule val tmpDir = TemporaryFolder()

  private class Fixture {
    lateinit var context: Context
    lateinit var shadowActivityManager: ShadowActivityManager
    lateinit var lastReportedMemoryLimiterFile: File
    lateinit var lastReportedAnrFile: File
    lateinit var lastReportedTombstoneFile: File

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
      memoryLimiterEnabled: Boolean = true,
      reportHistoricalMemoryLimiterExits: Boolean = true,
      lastReportedTimestamp: Long? = null,
      anrReportedTimestamp: Long? = null,
      tombstoneReportedTimestamp: Long? = null,
      useImmediateExecutorService: Boolean = true,
      sdkVersion: Int = 37,
    ): MemoryLimiterIntegration {
      options.run {
        setLogger(this@Fixture.logger)
        isDebug = true
        cacheDirPath = dir?.newFolder()?.absolutePath
        executorService = if (useImmediateExecutorService) ImmediateExecutorService() else mock()
        isMemoryLimiterEnabled = memoryLimiterEnabled
        isReportHistoricalMemoryLimiterExits = reportHistoricalMemoryLimiterExits
        setEnvelopeDiskCache(EnvelopeCache.create(this))
      }

      options.cacheDirPath?.let { cacheDirPath ->
        val cacheDir = File(cacheDirPath).also { it.mkdirs() }
        lastReportedMemoryLimiterFile =
          File(cacheDir, AndroidEnvelopeCache.LAST_MEMORY_LIMITER_REPORT).apply {
            writeText(lastReportedTimestamp.toString())
          }
        lastReportedAnrFile =
          File(cacheDir, AndroidEnvelopeCache.LAST_ANR_REPORT).apply {
            writeText(anrReportedTimestamp.toString())
          }
        lastReportedTombstoneFile =
          File(cacheDir, AndroidEnvelopeCache.LAST_TOMBSTONE_REPORT).apply {
            writeText(tombstoneReportedTimestamp.toString())
          }
      }

      whenever(scopes.captureEvent(any(), anyOrNull<Hint>())).thenReturn(SentryId())
      val buildInfoProvider = mock<BuildInfoProvider>()
      whenever(buildInfoProvider.sdkInfoVersion).thenReturn(sdkVersion)
      return MemoryLimiterIntegration(context, CurrentDateProvider.getInstance(), buildInfoProvider)
    }

    fun addAppExitInfo(
      reason: Int = ApplicationExitInfo.REASON_OTHER,
      timestamp: Long,
      description: String? = MemoryLimiterIntegration.MEMORY_LIMITER_DESCRIPTION,
      importance: Int = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
    ) {
      val builder = ApplicationExitInfoBuilder.newBuilder()
      builder.setReason(reason)
      builder.setTimestamp(timestamp)
      builder.setImportance(importance)
      val exitInfo =
        org.mockito.kotlin.spy(builder.build()) {
          whenever(mock.description).thenReturn(description)
        }
      shadowActivityManager.addApplicationExitInfo(exitInfo)
    }
  }

  private val fixture = Fixture()
  private val oldTimestamp = System.currentTimeMillis() - 10_000
  private val newTimestamp = oldTimestamp + 5_000

  @BeforeTest
  fun `set up`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    fixture.init(context)
  }

  @AfterTest
  fun `tear down`() {
    fixture.options.cacheDirPath?.let { File(it).deleteRecursively() }
  }

  @Test
  fun `does not process MemoryLimiter exits when integration is disabled`() {
    val integration =
      fixture.getSut(
        memoryLimiterEnabled = false,
        dir = tmpDir,
        sdkVersion = 37,
        useImmediateExecutorService = false,
      )

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.options.executorService, never()).submit(any())
  }

  @Test
  fun `does not process MemoryLimiter exits when cache dir is not set`() {
    val integration =
      fixture.getSut(
        memoryLimiterEnabled = true,
        dir = null,
        sdkVersion = 37,
        useImmediateExecutorService = false,
      )

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.options.executorService, never()).submit(any())
  }

  @Test
  fun `does not process MemoryLimiter exits when Android is below API 37`() {
    val integration =
      fixture.getSut(
        memoryLimiterEnabled = true,
        sdkVersion = 36,
        dir = tmpDir,
        useImmediateExecutorService = false,
      )

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.options.executorService, never()).submit(any())
  }

  @Test
  fun `processes MemoryLimiter exits when integration is enabled, cache dir is set, and Android is API 37`() {
    val integration =
      fixture.getSut(
        memoryLimiterEnabled = true,
        dir = tmpDir,
        sdkVersion = 37,
        useImmediateExecutorService = false,
      )

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.options.executorService).submit(any())
  }

  @Test
  fun `processes MemoryLimiter exits when integration is enabled, cache dir is set, and Android is above API 37`() {
    val integration =
      fixture.getSut(
        memoryLimiterEnabled = true,
        dir = tmpDir,
        sdkVersion = 38,
        useImmediateExecutorService = false,
      )

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.options.executorService).submit(any())
  }

  @Test
  fun `captures exit when reason and description match exits produced by MemoryLimiter`() {
    val integration =
      fixture.getSut(
        memoryLimiterEnabled = true,
        reportHistoricalMemoryLimiterExits = true,
        dir = tmpDir,
        sdkVersion = 37,
        lastReportedTimestamp = oldTimestamp,
      )
    fixture.addAppExitInfo(
      reason = ApplicationExitInfo.REASON_OTHER,
      description = MemoryLimiterIntegration.MEMORY_LIMITER_DESCRIPTION,
      timestamp = newTimestamp,
    )

    integration.register(fixture.scopes, fixture.options)

    val expectedMessage =
      MemoryLimiterIntegration.MEMORY_LIMITER_MESSAGE_PREFIX + " (importance: foreground)"

    verify(fixture.scopes)
      .captureEvent(
        check<SentryEvent> { event ->
          assertEquals(SentryLevel.FATAL, event.level)
          assertEquals(newTimestamp, event.timestamp!!.time)
          assertEquals(expectedMessage, event.message!!.formatted)
          assertEquals("java", event.platform)
          assertEquals(
            listOf(
              MemoryLimiterIntegration.MEMORY_LIMITER_FINGERPRINT,
              MemoryLimiterIntegration.PROCESS_IMPORTANCE_FINGERPRINT_PREFIX +
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
            ),
            event.fingerprints,
          )

          val exception = event.exceptions!!.single()
          assertEquals("MemoryLimitExceeded", exception.type)
          assertEquals(expectedMessage, exception.value)
          assertEquals("io.sentry.android.core", exception.module)

          val mechanism = exception.mechanism!!
          assertEquals("AppExitInfo", mechanism.type)
          assertEquals(
            MemoryLimiterIntegration.MEMORY_LIMITER_DESCRIPTION,
            mechanism.description,
          )
          assertEquals(false, mechanism.isHandled)
          assertEquals(true, mechanism.synthetic)
          assertEquals(
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND.toString() +
              " (foreground)",
            mechanism.data!![MemoryLimiterIntegration.PROCESS_IMPORTANCE_DATA_KEY],
          )
          assertEquals(
            MemoryLimiterIntegration.MEMORY_LIMIT_CLASS_VISIBLE,
            mechanism.data!![MemoryLimiterIntegration.MEMORY_LIMIT_CLASS_DATA_KEY],
          )
        },
        argThat<Hint> {
          val hint = HintUtils.getSentrySdkHint(this) as MemoryLimiterHint
          hint.shouldEnrich() && hint.timestamp() == newTimestamp
        },
      )
  }

  @Test
  fun `ignores exit when reason does not match exits produced by MemoryLimiter`() {
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp)
    fixture.addAppExitInfo(
      reason = ApplicationExitInfo.REASON_ANR,
      description = MemoryLimiterIntegration.MEMORY_LIMITER_DESCRIPTION,
      timestamp = newTimestamp,
    )

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
  }

  @Test
  fun `ignores exit when description does not match exits produced by MemoryLimiter`() {
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp)
    fixture.addAppExitInfo(
      reason = ApplicationExitInfo.REASON_OTHER,
      description = "LowSwapKiller",
      timestamp = newTimestamp,
    )

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
  }

  @Test
  fun `captures exit for any MemoryLimiter sub-reason, not just AnonSwap`() {
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp)
    // A future MemoryLimiter kill sub-reason (e.g. the memory or swap limits) still lives in the
    // "MemoryLimiter:" namespace and must be captured, with its raw sub-reason preserved.
    fixture.addAppExitInfo(
      reason = ApplicationExitInfo.REASON_OTHER,
      description = "MemoryLimiter:Memory",
      timestamp = newTimestamp,
    )

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes)
      .captureEvent(
        check<SentryEvent> { event ->
          assertEquals("MemoryLimiter:Memory", event.exceptions!!.single().mechanism!!.description)
        },
        anyOrNull<Hint>(),
      )
  }

  @Test
  fun `ignores exit when description mentions MemoryLimiter without the namespace delimiter`() {
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp)
    // "MemoryLimiter" without the ":" delimiter is not a MemoryLimiter kill; matching requires the
    // namespace prefix so we don't over-capture unrelated REASON_OTHER exits.
    fixture.addAppExitInfo(
      reason = ApplicationExitInfo.REASON_OTHER,
      description = "NotAMemoryLimiterKill",
      timestamp = newTimestamp,
    )

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
  }

  @Test
  fun `ignores exit when description is null`() {
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp)
    fixture.addAppExitInfo(
      reason = ApplicationExitInfo.REASON_OTHER,
      description = null,
      timestamp = newTimestamp,
    )

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
  }

  @Test
  fun `historical MemoryLimiter exits are reported oldest to newest`() {
    val integration =
      fixture.getSut(
        memoryLimiterEnabled = true,
        reportHistoricalMemoryLimiterExits = true,
        dir = tmpDir,
        sdkVersion = 37,
        lastReportedTimestamp = oldTimestamp,
      )
    fixture.addAppExitInfo(timestamp = newTimestamp - 2_000)
    fixture.addAppExitInfo(timestamp = newTimestamp - 1_000)
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    inOrder(fixture.scopes) {
      verify(fixture.scopes)
        .captureEvent(
          argThat { timestamp!!.time == newTimestamp - 2_000 },
          argThat<Hint> { !(HintUtils.getSentrySdkHint(this) as MemoryLimiterHint).shouldEnrich() },
        )
      verify(fixture.scopes)
        .captureEvent(
          argThat { timestamp!!.time == newTimestamp - 1_000 },
          argThat<Hint> { !(HintUtils.getSentrySdkHint(this) as MemoryLimiterHint).shouldEnrich() },
        )
      verify(fixture.scopes)
        .captureEvent(
          argThat { timestamp!!.time == newTimestamp },
          argThat<Hint> { (HintUtils.getSentrySdkHint(this) as MemoryLimiterHint).shouldEnrich() },
        )
    }
  }

  @Test
  fun `skips historical MemoryLimiter exits at or before the last reported timestamp`() {
    val skippedHistoricalTimestamp = newTimestamp - 2_000
    val reportedHistoricalTimestamp = newTimestamp - 1_000
    val integration =
      fixture.getSut(
        memoryLimiterEnabled = true,
        reportHistoricalMemoryLimiterExits = true,
        dir = tmpDir,
        sdkVersion = 37,
        lastReportedTimestamp = newTimestamp - 1_500,
      )
    fixture.addAppExitInfo(timestamp = skippedHistoricalTimestamp)
    fixture.addAppExitInfo(timestamp = reportedHistoricalTimestamp)
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, never())
      .captureEvent(argThat { timestamp!!.time == skippedHistoricalTimestamp }, anyOrNull<Hint>())
    inOrder(fixture.scopes) {
      verify(fixture.scopes)
        .captureEvent(
          argThat { timestamp!!.time == reportedHistoricalTimestamp },
          argThat<Hint> { !(HintUtils.getSentrySdkHint(this) as MemoryLimiterHint).shouldEnrich() },
        )
      verify(fixture.scopes)
        .captureEvent(
          argThat { timestamp!!.time == newTimestamp },
          argThat<Hint> { (HintUtils.getSentrySdkHint(this) as MemoryLimiterHint).shouldEnrich() },
        )
    }
  }

  @Test
  fun `does not report historical MemoryLimiter exits if historical exits are disabled`() {
    val integration =
      fixture.getSut(
        memoryLimiterEnabled = true,
        reportHistoricalMemoryLimiterExits = false,
        dir = tmpDir,
        sdkVersion = 37,
        lastReportedTimestamp = oldTimestamp,
      )
    fixture.addAppExitInfo(timestamp = newTimestamp - 2_000)
    fixture.addAppExitInfo(timestamp = newTimestamp - 1_000)
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    // Verify we report only the latest MemoryLimiter exit, and none before it.
    verify(fixture.scopes, atMost(1)).captureEvent(any(), anyOrNull<Hint>())
    assertTrue(fixture.lastReportedMemoryLimiterFile.exists())
  }

  @Test
  fun `does not report historical MemoryLimiter exits if integration is disabled`() {
    val integration =
      fixture.getSut(
        memoryLimiterEnabled = false,
        reportHistoricalMemoryLimiterExits = true,
        dir = tmpDir,
        sdkVersion = 37,
        useImmediateExecutorService = false,
      )

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.options.executorService, never()).submit(any())
  }

  @Test
  fun `skips MemoryLimiter exits that were already reported`() {
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = newTimestamp)
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
  }

  @Test
  fun `dedupes MemoryLimiter exists independently of ANR exits`() {
    val integration =
      fixture.getSut(
        tmpDir,
        lastReportedTimestamp = oldTimestamp,
        anrReportedTimestamp = newTimestamp,
      )
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes).captureEvent(any(), anyOrNull<Hint>())
  }

  @Test
  fun `dedupes MemoryLimiter exits independently of Tombstone exits`() {
    val integration =
      fixture.getSut(
        tmpDir,
        lastReportedTimestamp = oldTimestamp,
        tombstoneReportedTimestamp = newTimestamp,
      )
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes).captureEvent(any(), anyOrNull<Hint>())
  }
}
