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
import io.sentry.cache.EnvelopeCache
import io.sentry.protocol.SentryId
import io.sentry.test.ImmediateExecutorService
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowActivityManager
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder

// Our current Robolectric version (4.15) caps at API 35. All ApplicationExitInfo code paths we
// exercise are at API 31 or below, so we use the latter.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [31])
class ApplicationExitInfoCrossIntegrationTest {

  @get:Rule val tmpDir = TemporaryFolder()

  private class Fixture {
    lateinit var context: Context
    lateinit var shadowActivityManager: ShadowActivityManager

    fun init(appContext: Context) {
      context = appContext
      context.applicationInfo.nativeLibraryDir =
        "/data/app/~~gu-2hA9_Zg6tfIuDAbLpKA==/io.sentry.samples.android-MFqmKAMnl9AjNlHcO3mejA==/lib/arm64"
      val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
      shadowActivityManager = Shadow.extract(activityManager)
    }

    fun newOptions(
      dir: File,
      configure: SentryAndroidOptions.() -> Unit,
    ): SentryAndroidOptions {
      return SentryAndroidOptions().apply {
        val logger = mock<ILogger>()
        whenever(logger.isEnabled(any())).thenReturn(true)
        setLogger(logger)
        isDebug = true
        cacheDirPath = dir.absolutePath
        executorService = ImmediateExecutorService()
        setEnvelopeDiskCache(EnvelopeCache.create(this))
        addInAppInclude("io.sentry.samples")
        configure()
      }
    }

    fun addMemoryLimiterExit(timestamp: Long) {
      addExitInfo(
        reason = ApplicationExitInfo.REASON_OTHER,
        timestamp = timestamp,
        description = MemoryLimiterIntegration.MEMORY_LIMITER_DESCRIPTION,
        importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
      )
    }

    fun addAnrExit(timestamp: Long) {
      val builder = ApplicationExitInfoBuilder.newBuilder()
      builder.setReason(ApplicationExitInfo.REASON_ANR)
      builder.setTimestamp(timestamp)
      builder.setImportance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
      val exitInfo =
        spy(builder.build()) {
          whenever(mock.traceInputStream)
            .thenReturn(
              """
              Subject: Input dispatching timed out (7985007 com.example.app/com.example.app.ui.MainActivity (server) is not responding. Waited 5000ms for FocusEvent(hasFocus=false))
              Here are no Binder-related exception messages available.
              Pid(12233) have D state thread(tid:12236 name:Signal Catcher)


              ----- dumping pid: 12233 at 313446151 -----
              "main" prio=5 tid=1 Native
                | group="main" sCount=1 ucsCount=0 flags=1 obj=0x72c4c9a0 self=0xb40000779f142000
                | sysTid=12233 nice=-10 cgrp=top-app sched=0/0 handle=0x7d8e4c44f8
                at io.sentry.samples.MainActivity.blocked(MainActivity.java:42)
                at android.os.Looper.loopOnce(Looper.java:226)
                at android.os.Looper.loop(Looper.java:313)
              """
                .trimIndent()
                .byteInputStream()
            )
        }
      shadowActivityManager.addApplicationExitInfo(exitInfo)
    }

    fun addTombstoneExit(timestamp: Long) {
      val builder = ApplicationExitInfoBuilder.newBuilder()
      builder.setReason(ApplicationExitInfo.REASON_CRASH_NATIVE)
      builder.setTimestamp(timestamp)
      builder.setImportance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
      val exitInfo =
        spy(builder.build()) {
          whenever(mock.traceInputStream)
            .thenReturn(
              GZIPInputStream(
                TombstoneIntegrationTest::class.java.getResourceAsStream("/tombstone.pb.gz")
              )
            )
        }
      shadowActivityManager.addApplicationExitInfo(exitInfo)
    }

    private fun addExitInfo(
      reason: Int,
      timestamp: Long,
      description: String?,
      importance: Int,
    ) {
      val builder = ApplicationExitInfoBuilder.newBuilder()
      builder.setReason(reason)
      builder.setTimestamp(timestamp)
      builder.setImportance(importance)
      val exitInfo =
        spy(builder.build()) {
          whenever(mock.description).thenReturn(description)
        }
      shadowActivityManager.addApplicationExitInfo(exitInfo)
    }
  }

  private val fixture = Fixture()

  @BeforeTest
  fun `set up`() {
    fixture.init(ApplicationProvider.getApplicationContext())
  }

  @AfterTest
  fun `tear down`() {
    tmpDir.root.deleteRecursively()
  }

  @Test
  fun `memory limiter and anr integrations each capture only their own exits from shared history`() {
    val memoryLimiterTimestamp = System.currentTimeMillis() - 1_000
    val anrTimestamp = memoryLimiterTimestamp + 500
    fixture.addMemoryLimiterExit(memoryLimiterTimestamp)
    fixture.addAnrExit(anrTimestamp)

    val memoryLimiterScopes = mock<IScopes>()
    whenever(memoryLimiterScopes.captureEvent(any(), anyOrNull<Hint>())).thenReturn(SentryId())
    val memoryLimiterOptions =
      fixture.newOptions(tmpDir.newFolder("memory-limiter")) {
        isMemoryLimiterEnabled = true
        isReportHistoricalMemoryLimiterExits = true
      }
    val buildInfoProvider = mock<BuildInfoProvider>()
    whenever(buildInfoProvider.sdkInfoVersion).thenReturn(37)
    MemoryLimiterIntegration(
        fixture.context,
        io.sentry.transport.CurrentDateProvider.getInstance(),
        buildInfoProvider,
      )
      .register(memoryLimiterScopes, memoryLimiterOptions)

    verify(memoryLimiterScopes)
      .captureEvent(
        argThat<SentryEvent> { timestamp!!.time == memoryLimiterTimestamp },
        anyOrNull<Hint>(),
      )
    verify(memoryLimiterScopes, never())
      .captureEvent(
        argThat<SentryEvent> { timestamp!!.time == anrTimestamp },
        anyOrNull<Hint>(),
      )

    val anrScopes = mock<IScopes>()
    whenever(anrScopes.captureEvent(any(), anyOrNull<Hint>())).thenReturn(SentryId())
    val anrOptions =
      fixture.newOptions(tmpDir.newFolder("anr")) {
        isAnrEnabled = true
        isReportHistoricalAnrs = true
      }
    AnrV2Integration(fixture.context).register(anrScopes, anrOptions)

    verify(anrScopes)
      .captureEvent(
        argThat<SentryEvent> { timestamp!!.time == anrTimestamp },
        anyOrNull<Hint>(),
      )
    verify(anrScopes, never())
      .captureEvent(
        argThat<SentryEvent> { timestamp!!.time == memoryLimiterTimestamp },
        anyOrNull<Hint>(),
      )
  }

  @Test
  fun `memory limiter and tombstone integrations each capture only their own exits from shared history`() {
    val memoryLimiterTimestamp = System.currentTimeMillis() - 1_000
    val tombstoneTimestamp = memoryLimiterTimestamp + 500
    fixture.addMemoryLimiterExit(memoryLimiterTimestamp)
    fixture.addTombstoneExit(tombstoneTimestamp)

    val memoryLimiterScopes = mock<IScopes>()
    whenever(memoryLimiterScopes.captureEvent(any(), anyOrNull<Hint>())).thenReturn(SentryId())
    val memoryLimiterOptions =
      fixture.newOptions(tmpDir.newFolder("memory-limiter")) {
        isMemoryLimiterEnabled = true
        isReportHistoricalMemoryLimiterExits = true
      }
    val buildInfoProvider = mock<BuildInfoProvider>()
    whenever(buildInfoProvider.sdkInfoVersion).thenReturn(37)
    MemoryLimiterIntegration(
        fixture.context,
        io.sentry.transport.CurrentDateProvider.getInstance(),
        buildInfoProvider,
      )
      .register(memoryLimiterScopes, memoryLimiterOptions)

    verify(memoryLimiterScopes)
      .captureEvent(
        argThat<SentryEvent> { timestamp!!.time == memoryLimiterTimestamp },
        anyOrNull<Hint>(),
      )
    verify(memoryLimiterScopes, never())
      .captureEvent(
        argThat<SentryEvent> { timestamp!!.time == tombstoneTimestamp },
        anyOrNull<Hint>(),
      )

    val tombstoneScopes = mock<IScopes>()
    whenever(tombstoneScopes.captureEvent(any(), anyOrNull<Hint>())).thenReturn(SentryId())
    val tombstoneOptions =
      fixture.newOptions(tmpDir.newFolder("tombstone")) {
        isTombstoneEnabled = true
        isReportHistoricalTombstones = true
      }
    TombstoneIntegration(fixture.context).register(tombstoneScopes, tombstoneOptions)

    verify(tombstoneScopes)
      .captureEvent(
        argThat<SentryEvent> { timestamp!!.time == tombstoneTimestamp },
        anyOrNull<Hint>(),
      )
    verify(tombstoneScopes, never())
      .captureEvent(
        argThat<SentryEvent> { timestamp!!.time == memoryLimiterTimestamp },
        anyOrNull<Hint>(),
      )
  }
}
