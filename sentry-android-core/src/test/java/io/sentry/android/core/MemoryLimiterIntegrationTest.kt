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
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowActivityManager
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder

// TODO ADAM: Refine tests.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class MemoryLimiterIntegrationTest {

  @get:Rule val tmpDir = TemporaryFolder()

  private class Fixture {
    lateinit var context: Context
    lateinit var shadowActivityManager: ShadowActivityManager
    lateinit var lastReportedMemoryLimiterFile: File
    lateinit var lastReportedAnrFile: File

    val options = SentryAndroidOptions()
    val scopes = mock<IScopes>()
    val logger = mock<ILogger>()

    fun init(appContext: Context) {
      context = appContext
      val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
      shadowActivityManager = Shadow.extract(activityManager)
    }

    fun getSut(
      dir: TemporaryFolder,
      enabled: Boolean = true,
      reportHistorical: Boolean = true,
      lastReportedTimestamp: Long? = null,
      anrReportedTimestamp: Long? = null,
    ): MemoryLimiterIntegration {
      options.run {
        setLogger(logger)
        isDebug = true
        cacheDirPath = dir.newFolder().absolutePath
        executorService = ImmediateExecutorService()
        isMemoryLimiterEnabled = enabled
        isReportHistoricalMemoryLimiterKills = reportHistorical
        setEnvelopeDiskCache(EnvelopeCache.create(this))
      }

      val cacheDir = File(options.cacheDirPath!!).also { it.mkdirs() }
      lastReportedMemoryLimiterFile =
        File(cacheDir, AndroidEnvelopeCache.LAST_MEMORY_LIMITER_REPORT).apply {
          writeText(lastReportedTimestamp.toString())
        }
      lastReportedAnrFile =
        File(cacheDir, AndroidEnvelopeCache.LAST_ANR_REPORT).apply {
          writeText(anrReportedTimestamp.toString())
        }

      whenever(scopes.captureEvent(any(), anyOrNull<Hint>())).thenReturn(SentryId())
      return MemoryLimiterIntegration(context)
    }

    fun addAppExitInfo(
      reason: Int = ApplicationExitInfo.REASON_OTHER,
      timestamp: Long,
      description: String? = MemoryLimiterIntegration.MEMORY_LIMITER_DESCRIPTION,
    ) {
      val builder = ApplicationExitInfoBuilder.newBuilder()
      builder.setReason(reason)
      builder.setTimestamp(timestamp)
      val exitInfo =
        spy(builder.build()) {
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
  fun cleanup() {
    fixture.options.cacheDirPath?.let { File(it).deleteRecursively() }
  }

  @Test
  fun `when integration is disabled, does not process historical exits`() {
    val integration = fixture.getSut(tmpDir, enabled = false)
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
  }

  @Test
  fun `captures matching memory limiter exit`() {
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp)
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes)
      .captureEvent(
        check<SentryEvent> { event ->
          assertEquals(SentryLevel.FATAL, event.level)
          assertEquals(newTimestamp, event.timestamp.time)
          assertEquals(MemoryLimiterIntegration.MEMORY_LIMITER_MESSAGE, event.message!!.formatted)
          assertEquals("java", event.platform)
          val exception = event.exceptions!!.single()
          assertEquals("MemoryLimitExceeded", exception.type)
          assertEquals(MemoryLimiterIntegration.MEMORY_LIMITER_MESSAGE, exception.value)
          assertEquals("AppExitInfo", exception.mechanism!!.type)
          assertEquals(
            MemoryLimiterIntegration.MEMORY_LIMITER_DESCRIPTION,
            exception.mechanism!!.description,
          )
        },
        argThat<Hint> {
          val hint = HintUtils.getSentrySdkHint(this) as MemoryLimiterHint
          hint.shouldEnrich() && hint.timestamp() == newTimestamp
        },
      )
  }

  @Test
  fun `does not capture REASON_OTHER with unrelated description`() {
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp)
    fixture.addAppExitInfo(timestamp = newTimestamp, description = "LowSwapKiller")

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
  }

  @Test
  fun `does not capture exit when description is null`() {
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp)
    fixture.addAppExitInfo(timestamp = newTimestamp, description = null)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
  }

  @Test
  fun `memory limiter marker suppresses already reported exit`() {
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = newTimestamp)
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
  }

  @Test
  fun `anr marker does not suppress matching memory limiter exit`() {
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
  fun `historical memory limiter exits are reported oldest to newest`() {
    val integration =
      fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp, reportHistorical = true)
    fixture.addAppExitInfo(timestamp = newTimestamp - 2_000)
    fixture.addAppExitInfo(timestamp = newTimestamp - 1_000)
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    inOrder(fixture.scopes) {
      verify(fixture.scopes)
        .captureEvent(
          argThat { timestamp.time == newTimestamp - 2_000 },
          argThat<Hint> { !(HintUtils.getSentrySdkHint(this) as MemoryLimiterHint).shouldEnrich() },
        )
      verify(fixture.scopes)
        .captureEvent(
          argThat { timestamp.time == newTimestamp - 1_000 },
          argThat<Hint> { !(HintUtils.getSentrySdkHint(this) as MemoryLimiterHint).shouldEnrich() },
        )
      verify(fixture.scopes)
        .captureEvent(
          argThat { timestamp.time == newTimestamp },
          argThat<Hint> { (HintUtils.getSentrySdkHint(this) as MemoryLimiterHint).shouldEnrich() },
        )
    }
  }

  @Test
  fun `when historical flag is disabled, reports only latest matching exit`() {
    val integration =
      fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp, reportHistorical = false)
    fixture.addAppExitInfo(timestamp = newTimestamp - 2_000)
    fixture.addAppExitInfo(timestamp = newTimestamp - 1_000)
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, atMost(1)).captureEvent(any(), anyOrNull<Hint>())
    assertTrue(fixture.lastReportedMemoryLimiterFile.exists())
  }
}
