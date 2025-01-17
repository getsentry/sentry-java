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
import io.sentry.android.core.AnrV2Integration.AnrV2Hint
import io.sentry.android.core.cache.AndroidEnvelopeCache
import io.sentry.cache.EnvelopeCache
import io.sentry.hints.DiskFlushNotification
import io.sentry.hints.SessionStartHint
import io.sentry.protocol.SentryId
import io.sentry.test.ImmediateExecutorService
import io.sentry.util.HintUtils
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
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class AnrV2IntegrationTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    class Fixture {
        lateinit var context: Context
        lateinit var shadowActivityManager: ShadowActivityManager
        lateinit var lastReportedAnrFile: File

        val options = SentryAndroidOptions()
        val scopes = mock<IScopes>()
        val logger = mock<ILogger>()

        fun getSut(
            dir: TemporaryFolder?,
            useImmediateExecutorService: Boolean = true,
            isAnrEnabled: Boolean = true,
            flushTimeoutMillis: Long = 0L,
            sessionFlushTimeoutMillis: Long = 0L,
            lastReportedAnrTimestamp: Long? = null,
            lastEventId: SentryId = SentryId(),
            sessionTrackingEnabled: Boolean = true,
            reportHistoricalAnrs: Boolean = true,
            attachAnrThreadDump: Boolean = false
        ): AnrV2Integration {
            options.run {
                setLogger(this@Fixture.logger)
                isDebug = true
                cacheDirPath = dir?.newFolder()?.absolutePath
                executorService =
                    if (useImmediateExecutorService) ImmediateExecutorService() else mock()
                this.isAnrEnabled = isAnrEnabled
                this.flushTimeoutMillis = flushTimeoutMillis
                this.sessionFlushTimeoutMillis = sessionFlushTimeoutMillis
                this.isEnableAutoSessionTracking = sessionTrackingEnabled
                this.isReportHistoricalAnrs = reportHistoricalAnrs
                this.isAttachAnrThreadDump = attachAnrThreadDump
                addInAppInclude("io.sentry.samples")
                setEnvelopeDiskCache(EnvelopeCache.create(this))
            }
            options.cacheDirPath?.let { cacheDir ->
                lastReportedAnrFile = File(cacheDir, AndroidEnvelopeCache.LAST_ANR_REPORT)
                lastReportedAnrFile.writeText(lastReportedAnrTimestamp.toString())
            }
            whenever(scopes.captureEvent(any(), anyOrNull<Hint>())).thenReturn(lastEventId)
            return AnrV2Integration(context)
        }

        fun addAppExitInfo(
            reason: Int? = ApplicationExitInfo.REASON_ANR,
            timestamp: Long? = null,
            importance: Int? = null,
            addTrace: Boolean = true,
            addBadTrace: Boolean = false
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
            val exitInfo = spy(builder.build()) {
                if (!addTrace) {
                    return
                }
                if (addBadTrace) {
                    whenever(mock.traceInputStream).thenReturn(
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
                        """.trimIndent().byteInputStream()
                    )
                } else {
                    whenever(mock.traceInputStream).thenReturn(
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
                        """.trimIndent().byteInputStream()
                    )
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
    fun `when anr tracking is not enabled, does not process historical exits`() {
        val integration =
            fixture.getSut(tmpDir, isAnrEnabled = false, useImmediateExecutorService = false)

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
    fun `when there are no ANRs in historical exits, does not capture events`() {
        val integration = fixture.getSut(tmpDir)
        fixture.addAppExitInfo(reason = null)

        integration.register(fixture.scopes, fixture.options)

        verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
    }

    @Test
    fun `when latest ANR is older than 90 days, does not capture events`() {
        val oldTimestamp = System.currentTimeMillis() -
            AnrV2Integration.NINETY_DAYS_THRESHOLD -
            TimeUnit.DAYS.toMillis(2)
        val integration = fixture.getSut(tmpDir)
        fixture.addAppExitInfo(timestamp = oldTimestamp)

        integration.register(fixture.scopes, fixture.options)

        verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
    }

    @Test
    fun `when latest ANR has already been reported, does not capture events`() {
        val integration = fixture.getSut(tmpDir, lastReportedAnrTimestamp = oldTimestamp)
        fixture.addAppExitInfo(timestamp = oldTimestamp)

        integration.register(fixture.scopes, fixture.options)

        verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
    }

    @Test
    fun `when no ANRs have ever been reported, captures events`() {
        val integration = fixture.getSut(tmpDir, lastReportedAnrTimestamp = null)
        fixture.addAppExitInfo(timestamp = oldTimestamp)

        integration.register(fixture.scopes, fixture.options)

        verify(fixture.scopes).captureEvent(any(), anyOrNull<Hint>())
    }

    @Test
    fun `when latest ANR has not been reported, captures event with enriching`() {
        val integration = fixture.getSut(tmpDir, lastReportedAnrTimestamp = oldTimestamp)
        fixture.addAppExitInfo(timestamp = newTimestamp)

        integration.register(fixture.scopes, fixture.options)

        verify(fixture.scopes).captureEvent(
            check {
                assertEquals(newTimestamp, it.timestamp.time)
                assertEquals(SentryLevel.FATAL, it.level)
                val mainThread = it.threads!!.first()
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
                val otherThread = it.threads!![1]
                assertEquals("perfetto_hprof_listener", otherThread.name)
                assertEquals(7, otherThread.id)
                assertEquals("Native", otherThread.state)
                assertEquals(false, otherThread.isCrashed)
                assertEquals(false, otherThread.isMain)
                val firstFrame = otherThread.stacktrace!!.frames!!.first()
                assertEquals(
                    "/apex/com.android.runtime/lib64/bionic/libc.so",
                    firstFrame.`package`
                )
                assertEquals("__start_thread", firstFrame.function)
                assertEquals(64, firstFrame.lineno)
		assertEquals("0x00000000000530b8", firstFrame.instructionAddr)
		assertEquals("native", firstFrame.platform)

		val image = it.debugMeta?.images?.find {
		    it.debugId == "741f3301-bbb0-b92c-58bd-c15282b8ec7b"
		}
		assertNotNull(image)
		assertEquals("/apex/com.android.runtime/lib64/bionic/libc.so", image.codeFile)
            },
            argThat<Hint> {
                val hint = HintUtils.getSentrySdkHint(this)
                (hint as AnrV2Hint).shouldEnrich()
            }
        )
    }

    @Test
    fun `when latest ANR has foreground importance, sets abnormal mechanism to anr_foreground`() {
        val integration = fixture.getSut(tmpDir, lastReportedAnrTimestamp = oldTimestamp)
        fixture.addAppExitInfo(
            timestamp = newTimestamp,
            importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        )

        integration.register(fixture.scopes, fixture.options)

        verify(fixture.scopes).captureEvent(
            any(),
            argThat<Hint> {
                val hint = HintUtils.getSentrySdkHint(this)
                (hint as AnrV2Hint).mechanism() == "anr_foreground"
            }
        )
    }

    @Test
    fun `waits for ANR events to be flushed on disk`() {
        val integration = fixture.getSut(
            tmpDir,
            lastReportedAnrTimestamp = oldTimestamp,
            flushTimeoutMillis = 500L
        )
        fixture.addAppExitInfo(timestamp = newTimestamp)

        whenever(fixture.scopes.captureEvent(any(), any<Hint>())).thenAnswer { invocation ->
            val hint = HintUtils.getSentrySdkHint(invocation.getArgument(1))
                as DiskFlushNotification
            thread {
                Thread.sleep(200L)
                hint.markFlushed()
            }
            SentryId()
        }

        integration.register(fixture.scopes, fixture.options)

        verify(fixture.scopes).captureEvent(any(), anyOrNull<Hint>())
        // shouldn't fall into timed out state, because we marked event as flushed on another thread
        verify(fixture.logger, never()).log(
            any(),
            argThat { startsWith("Timed out waiting to flush ANR event to disk.") },
            any<Any>()
        )
    }

    @Test
    fun `when latest ANR event was dropped, does not block flushing`() {
        val integration = fixture.getSut(
            tmpDir,
            lastReportedAnrTimestamp = oldTimestamp,
            lastEventId = SentryId.EMPTY_ID
        )
        fixture.addAppExitInfo(timestamp = newTimestamp)

        integration.register(fixture.scopes, fixture.options)

        verify(fixture.scopes).captureEvent(any(), anyOrNull<Hint>())
        // we do not call markFlushed, hence it should time out waiting for flush, but because
        // we drop the event, it should not even come to this if-check
        verify(fixture.logger, never()).log(
            any(),
            argThat { startsWith("Timed out waiting to flush ANR event to disk.") },
            any<Any>()
        )
    }

    @Test
    fun `historical ANRs are reported non-enriched`() {
        val integration = fixture.getSut(tmpDir, lastReportedAnrTimestamp = oldTimestamp)
        fixture.addAppExitInfo(timestamp = newTimestamp - 2 * 60 * 1000)
        fixture.addAppExitInfo(timestamp = newTimestamp - 1 * 60 * 1000)
        fixture.addAppExitInfo(timestamp = newTimestamp)

        integration.register(fixture.scopes, fixture.options)

        verify(fixture.scopes, times(2)).captureEvent(
            any(),
            argThat<Hint> {
                val hint = HintUtils.getSentrySdkHint(this)
                !(hint as AnrV2Hint).shouldEnrich()
            }
        )
    }

    @Test
    fun `when historical ANRs flag is disabled, does not report`() {
        val integration = fixture.getSut(
            tmpDir,
            lastReportedAnrTimestamp = oldTimestamp,
            reportHistoricalAnrs = false
        )
        fixture.addAppExitInfo(timestamp = newTimestamp - 2 * 60 * 1000)
        fixture.addAppExitInfo(timestamp = newTimestamp - 1 * 60 * 1000)
        fixture.addAppExitInfo(timestamp = newTimestamp)

        integration.register(fixture.scopes, fixture.options)

        // only the latest anr is reported which should be enrichable
        verify(fixture.scopes, atMost(1)).captureEvent(
            any(),
            argThat<Hint> {
                val hint = HintUtils.getSentrySdkHint(this)
                (hint as AnrV2Hint).shouldEnrich()
            }
        )
    }

    @Test
    fun `historical ANRs are reported in reverse order to keep track of last reported ANR in a marker file`() {
        val integration = fixture.getSut(tmpDir, lastReportedAnrTimestamp = oldTimestamp)
        // robolectric uses addFirst when adding exit infos, so the last one here will be the first on the list
        fixture.addAppExitInfo(timestamp = newTimestamp - TimeUnit.DAYS.toMillis(2))
        fixture.addAppExitInfo(timestamp = newTimestamp - TimeUnit.DAYS.toMillis(1))
        fixture.addAppExitInfo(timestamp = newTimestamp)

        integration.register(fixture.scopes, fixture.options)

        // the order is reverse here, so the oldest ANR will be reported first to keep track of
        // last reported ANR in a marker file
        inOrder(fixture.scopes) {
            verify(fixture.scopes).captureEvent(
                argThat { timestamp.time == newTimestamp - TimeUnit.DAYS.toMillis(2) },
                anyOrNull<Hint>()
            )
            verify(fixture.scopes).captureEvent(
                argThat { timestamp.time == newTimestamp - TimeUnit.DAYS.toMillis(1) },
                anyOrNull<Hint>()
            )
            verify(fixture.scopes).captureEvent(
                argThat { timestamp.time == newTimestamp },
                anyOrNull<Hint>()
            )
        }
    }

    @Test
    fun `ANR timestamp is passed with the hint`() {
        val integration = fixture.getSut(tmpDir, lastReportedAnrTimestamp = oldTimestamp)
        fixture.addAppExitInfo(timestamp = newTimestamp)

        integration.register(fixture.scopes, fixture.options)

        verify(fixture.scopes).captureEvent(
            any(),
            argThat<Hint> {
                val hint = HintUtils.getSentrySdkHint(this)
                (hint as AnrV2Hint).timestamp() == newTimestamp
            }
        )
    }

    @Test
    fun `abnormal mechanism is passed with the hint`() {
        val integration = fixture.getSut(tmpDir, lastReportedAnrTimestamp = oldTimestamp)
        fixture.addAppExitInfo(timestamp = newTimestamp)

        integration.register(fixture.scopes, fixture.options)

        verify(fixture.scopes).captureEvent(
            any(),
            argThat<Hint> {
                val hint = HintUtils.getSentrySdkHint(this)
                (hint as AnrV2Hint).mechanism() == "anr_background"
            }
        )
    }

    @Test
    fun `awaits for previous session flush if cache is EnvelopeCache`() {
        val integration = fixture.getSut(
            tmpDir,
            lastReportedAnrTimestamp = oldTimestamp,
            sessionFlushTimeoutMillis = 500L
        )
        fixture.addAppExitInfo(timestamp = newTimestamp)

        thread {
            Thread.sleep(200L)
            val sessionHint = HintUtils.createWithTypeCheckHint(SessionStartHint())
            fixture.options.envelopeDiskCache.store(
                SentryEnvelope(SentryId.EMPTY_ID, null, emptyList()),
                sessionHint
            )
        }

        integration.register(fixture.scopes, fixture.options)

        // we store envelope with StartSessionHint on different thread after some delay, which
        // triggers the previous session flush, so no timeout
        verify(fixture.logger, never()).log(
            any(),
            argThat { startsWith("Timed out waiting to flush previous session to its own file.") },
            any<Any>()
        )
    }

    @Test
    fun `does not await for previous session flush, if session tracking is disabled`() {
        val integration = fixture.getSut(
            tmpDir,
            lastReportedAnrTimestamp = oldTimestamp,
            sessionFlushTimeoutMillis = 500L,
            sessionTrackingEnabled = false
        )
        fixture.addAppExitInfo(timestamp = newTimestamp)

        integration.register(fixture.scopes, fixture.options)

        verify(fixture.logger, never()).log(
            any(),
            argThat { startsWith("Timed out waiting to flush previous session to its own file.") },
            any<Any>()
        )
        verify(fixture.scopes).captureEvent(any(), any<Hint>())
    }

    @Test
    fun `flushes previous session latch, if timed out waiting`() {
        val integration = fixture.getSut(
            tmpDir,
            lastReportedAnrTimestamp = oldTimestamp,
            sessionFlushTimeoutMillis = 500L
        )
        fixture.addAppExitInfo(timestamp = newTimestamp)

        integration.register(fixture.scopes, fixture.options)

        verify(fixture.logger).log(
            any(),
            argThat { startsWith("Timed out waiting to flush previous session to its own file.") },
            any<Any>()
        )
        // should return true, because latch is 0 now
        assertTrue((fixture.options.envelopeDiskCache as EnvelopeCache).waitPreviousSessionFlush())
    }

    @Test
    fun `attaches plain thread dump, if enabled`() {
        val integration = fixture.getSut(
            tmpDir,
            lastReportedAnrTimestamp = oldTimestamp,
            attachAnrThreadDump = true
        )
        fixture.addAppExitInfo(timestamp = newTimestamp)

        integration.register(fixture.scopes, fixture.options)

        verify(fixture.scopes).captureEvent(
            any(),
            check<Hint> {
                assertNotNull(it.threadDump)
            }
        )
    }

    @Test
    fun `when traceInputStream is null, does not report ANR`() {
        val integration = fixture.getSut(tmpDir, lastReportedAnrTimestamp = oldTimestamp)
        fixture.addAppExitInfo(timestamp = newTimestamp, addTrace = false)

        integration.register(fixture.scopes, fixture.options)

        verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
    }

    @Test
    fun `when traceInputStream has bad data, does not report ANR`() {
        val integration = fixture.getSut(tmpDir, lastReportedAnrTimestamp = oldTimestamp)
        fixture.addAppExitInfo(timestamp = newTimestamp, addBadTrace = true)

        integration.register(fixture.scopes, fixture.options)

        verify(fixture.scopes, never()).captureEvent(any(), anyOrNull<Hint>())
    }
}
