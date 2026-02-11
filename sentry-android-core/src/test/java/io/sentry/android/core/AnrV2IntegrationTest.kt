package io.sentry.android.core

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.android.core.AnrV2Integration.AnrV2Hint
import io.sentry.android.core.anr.AnrProfileManager
import io.sentry.android.core.anr.AnrProfileRotationHelper
import io.sentry.android.core.anr.AnrStackTrace
import io.sentry.android.core.cache.AndroidEnvelopeCache
import io.sentry.protocol.SentryId
import io.sentry.util.HintUtils
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.After
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class AnrV2IntegrationTest : ApplicationExitIntegrationTestBase<AnrV2Hint>() {

  override val config =
    IntegrationTestConfig(
      setEnabledFlag = { isAnrEnabled = it },
      setReportHistoricalFlag = { isReportHistoricalAnrs = it },
      createIntegration = { context -> AnrV2Integration(context) },
      lastReportedFileName = AndroidEnvelopeCache.LAST_ANR_REPORT,
      defaultExitReason = ApplicationExitInfo.REASON_ANR,
      hintAccessors =
        HintAccessors(
          cast = { it as AnrV2Hint },
          shouldEnrich = { it.shouldEnrich() },
          timestamp = { it.timestamp() },
        ),
      addExitInfo = { reason, timestamp, importance, addTrace, addBadTrace ->
        val builder = ApplicationExitInfoBuilder.newBuilder()
        reason?.let { builder.setReason(it) }
        timestamp?.let { builder.setTimestamp(it) }
        importance?.let { builder.setImportance(it) }
        val exitInfo =
          spy(builder.build()) {
            if (!addTrace) {
              return@spy
            }
            if (addBadTrace) {
              whenever(mock.traceInputStream)
                .thenReturn(
                  """
                            Subject: Input dispatching timed out (7985007 com.example.app/com.example.app.ui.MainActivity (server) is not responding. Waited 5000ms for FocusEvent(hasFocus=false))
                            Here are no Binder-related exception messages available.
                            Pid(12233) have D state thread(tid:12236 name:Signal Catcher)


                            RssHwmKb: 823716
                            RssKb: 548348
                            RssAnonKb: 382156
                            RssShmemKb: 13304
                            VmSwapKb: 82484


                            --- CriticalEventLog ---
                            capacity: 20
                            timestamp_ms: 1731507490032
                            window_ms: 300000

                            ----- dumping pid: 12233 at 313446151
                            libdebuggerd_client: unexpected registration response: 0

                            ----- Waiting Channels: pid 12233 at 2024-11-13 19:48:09.980104540+0530 -----
                            Cmd line: com.example.app:mainProcess
                            """
                    .trimIndent()
                    .byteInputStream()
                )
            } else {
              whenever(mock.traceInputStream)
                .thenReturn(
                  """
"main" prio=5 tid=1 Blocked
  | group="main" sCount=1 ucsCount=0 flags=1 obj=0x72a985e0 self=0xb400007cabc57380
  | sysTid=28941 nice=-10 cgrp=top-app sched=0/0 handle=0x7deceb74f8
  | state=S schedstat=( 324804784 183300334 997 ) utm=23 stm=8 core=3 HZ=100
  | stack=0x7ff93a9000-0x7ff93ab000 stackSize=8188KB
  | held mutexes=
  at io.sentry.samples.android.MainActivity${'$'}2.run(MainActivity.java:177)
  - waiting to lock <0x0d3a2f0a> (a java.lang.Object) held by thread 5
  at android.os.Handler.handleCallback(Handler.java:942)
  at android.os.Handler.dispatchMessage(Handler.java:99)
  at android.os.Looper.loopOnce(Looper.java:201)
  at android.os.Looper.loop(Looper.java:288)
  at android.app.ActivityThread.main(ActivityThread.java:7872)
  at java.lang.reflect.Method.invoke(Native method)
  at com.android.internal.os.RuntimeInit${'$'}MethodAndArgsCaller.run(RuntimeInit.java:548)
  at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:936)

"perfetto_hprof_listener" prio=10 tid=7 Native (still starting up)
  | group="" sCount=1 ucsCount=0 flags=1 obj=0x0 self=0xb400007cabc5ab20
  | sysTid=28959 nice=-20 cgrp=top-app sched=0/0 handle=0x7b2021bcb0
  | state=S schedstat=( 72750 1679167 1 ) utm=0 stm=0 core=3 HZ=100
  | stack=0x7b20124000-0x7b20126000 stackSize=991KB
  | held mutexes=
  native: #00 pc 00000000000a20f4  /apex/com.android.runtime/lib64/bionic/libc.so (read+4) (BuildId: 01331f74b0bb2cb958bdc15282b8ec7b)
  native: #01 pc 000000000001d840  /apex/com.android.art/lib64/libperfetto_hprof.so (void* std::__1::__thread_proxy<std::__1::tuple<std::__1::unique_ptr<std::__1::__thread_struct, std::__1::default_delete<std::__1::__thread_struct> >, ArtPlugin_Initialize::${'$'}_34> >(void*)+260) (BuildId: 525cc92a7dc49130157aeb74f6870364)
  native: #02 pc 00000000000b63b0  /apex/com.android.runtime/lib64/bionic/libc.so (__pthread_start(void*)+208) (BuildId: 01331f74b0bb2cb958bdc15282b8ec7b)
  native: #03 pc 00000000000530b8  /apex/com.android.runtime/lib64/bionic/libc.so (__start_thread+64) (BuildId: 01331f74b0bb2cb958bdc15282b8ec7b)
  (no managed stack frames)
                            """
                    .trimIndent()
                    .byteInputStream()
                )
            }
          }
        shadowActivityManager.addApplicationExitInfo(exitInfo)
      },
      flushLogPrefix = "Timed out waiting to flush ANR event to disk.",
    )

  override fun assertEnrichedEvent(event: SentryEvent) {
    val mainThread = event.threads!!.first()
    assertEquals("main", mainThread.name)
    assertEquals(1, mainThread.id)
    assertEquals("Blocked", mainThread.state)
    assertEquals(true, mainThread.isCrashed)
    assertEquals(true, mainThread.isMain)
    assertEquals("0x0d3a2f0a", mainThread.heldLocks!!.values.first().address)
    assertEquals(5, mainThread.heldLocks!!.values.first().threadId)

    val lastFrame = mainThread.stacktrace!!.frames!!.last()
    assertEquals("io.sentry.samples.android.MainActivity$2", lastFrame.module)
    assertEquals("MainActivity.java", lastFrame.filename)
    assertEquals("run", lastFrame.function)
    assertEquals(177, lastFrame.lineno)
    assertEquals(true, lastFrame.isInApp)

    val otherThread = event.threads!![1]
    assertEquals("perfetto_hprof_listener", otherThread.name)
    assertEquals(7, otherThread.id)
    assertEquals("Native", otherThread.state)
    assertEquals(false, otherThread.isCrashed)
    assertEquals(false, otherThread.isMain)

    val firstFrame = otherThread.stacktrace!!.frames!!.first()
    assertEquals("/apex/com.android.runtime/lib64/bionic/libc.so", firstFrame.`package`)
    assertEquals("__start_thread", firstFrame.function)
    assertEquals(64, firstFrame.lineno)
    assertEquals("0x00000000000530b8", firstFrame.instructionAddr)
    assertEquals("native", firstFrame.platform)
    assertEquals("rel:741f3301-bbb0-b92c-58bd-c15282b8ec7b", firstFrame.addrMode)

    val image =
      event.debugMeta?.images?.find { it.debugId == "741f3301-bbb0-b92c-58bd-c15282b8ec7b" }
    assertNotNull(image)
    assertEquals("/apex/com.android.runtime/lib64/bionic/libc.so", image.codeFile)
  }

  @After
  fun cleanup() {
    fixture.options.cacheDirPath?.let { File(it).deleteRecursively() }
  }

  @Test
  fun `when latest ANR has foreground importance, sets abnormal mechanism to anr_foreground`() {
    val integration =
      fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp, sessionTrackingEnabled = true)
    fixture.addAppExitInfo(
      timestamp = newTimestamp,
      importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
    )

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes)
      .captureEvent(
        any(),
        argThat<Hint> {
          val hint = HintUtils.getSentrySdkHint(this)
          (hint as AnrV2Hint).mechanism() == "anr_foreground"
        },
      )
  }

  @Test
  fun `abnormal mechanism is passed with the hint`() {
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp)
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes)
      .captureEvent(
        any(),
        argThat<Hint> {
          val hint = HintUtils.getSentrySdkHint(this)
          (hint as AnrV2Hint).mechanism() == "anr_background"
        },
      )
  }

  @Test
  fun `attaches plain thread dump, if enabled`() {
    val integration =
      fixture.getSut(
        tmpDir,
        lastReportedTimestamp = oldTimestamp,
        extraOptions = { opts -> opts.isAttachAnrThreadDump = true },
      )
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes).captureEvent(any(), check<Hint> { assertNotNull(it.threadDump) })
  }

  @Test
  fun `when traceInputStream is null, does not report ANR`() {
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp)
    fixture.addAppExitInfo(timestamp = newTimestamp, addTrace = false)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
  }

  @Test
  fun `when traceInputStream has bad data, does not report ANR`() {
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp)
    fixture.addAppExitInfo(timestamp = newTimestamp, addBadTrace = true)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
  }

  @Test
  fun `when ANR has only system frames, static fingerprint is set`() {
    fixture.options.dsn = "https://key@sentry.io/proj"
    fixture.options.isEnableAnrProfiling = true
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp)

    val stack =
      arrayOf(
        StackTraceElement("android.view.Choreographer", "doFrame", "Choreographer.java", 1234),
        StackTraceElement("android.os.Handler", "dispatchMessage", "Handler.java", 5678),
      )

    val profileManager =
      AnrProfileManager(
        fixture.options,
        AnrProfileRotationHelper.getFileForRecording(File(fixture.options.cacheDirPath!!)),
      )
    profileManager.add(AnrStackTrace(newTimestamp, stack))
    profileManager.close()
    AnrProfileRotationHelper.rotate()

    fixture.addAppExitInfo(
      timestamp = newTimestamp,
      importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
    )

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes)
      .captureEvent(
        check {
          assertNotNull(it.fingerprints)
          assertEquals(2, it.fingerprints!!.size)
          assertEquals("{{ system-frames-only-anr }}", it.fingerprints!![0])
          assertEquals("foreground-anr", it.fingerprints!![1])
        },
        anyOrNull<Hint>(),
      )
  }

  @Test
  fun `when ANR has app frames, static fingerprints are not set`() {
    fixture.options.dsn = "https://key@sentry.io/proj"
    fixture.options.isEnableAnrProfiling = true
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp)

    val stack =
      arrayOf(
        StackTraceElement("com.example.MyApp", "onCreate", "MyApp.java", 1234),
        StackTraceElement("android.view.Choreographer", "doFrame", "Choreographer.java", 1234),
        StackTraceElement("android.os.Handler", "dispatchMessage", "Handler.java", 5678),
      )

    val profileManager =
      AnrProfileManager(
        fixture.options,
        AnrProfileRotationHelper.getFileForRecording(File(fixture.options.cacheDirPath!!)),
      )
    profileManager.add(AnrStackTrace(newTimestamp, stack))
    profileManager.close()
    AnrProfileRotationHelper.rotate()

    fixture.addAppExitInfo(
      timestamp = newTimestamp,
      importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
    )

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes).captureEvent(check { assertNull(it.fingerprints) }, anyOrNull<Hint>())
  }

  @Test
  fun `when ANR profiling is disabled, does not set custom fingerprint`() {
    fixture.options.isEnableAnrProfiling = false
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp)
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes)
      .captureEvent(check { assertEquals(null, it.fingerprints) }, anyOrNull<Hint>())
  }

  @Test
  fun `when captureProfileChunk returns empty ID, does not set profile context`() {
    fixture.options.isEnableAnrProfiling = true
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp)
    fixture.addAppExitInfo(timestamp = newTimestamp)
    whenever(fixture.scopes.captureProfileChunk(any())).thenReturn(SentryId.EMPTY_ID)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes)
      .captureEvent(check { assertEquals(null, it.contexts.profile) }, anyOrNull<Hint>())
  }

  @Test
  fun `when cacheDirPath is null, does not apply ANR profile`() {
    fixture.options.isEnableAnrProfiling = true
    fixture.options.cacheDirPath = null
    val integration = fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp)
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes, never()).captureProfileChunk(any())
  }
}
