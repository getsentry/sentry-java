package io.sentry.android.replay

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat.JPEG
import android.graphics.Bitmap.Config.ARGB_8888
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.EventProcessor
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.SentryReplayEvent
import io.sentry.SentryReplayEvent.ReplayType
import io.sentry.SystemOutLogger
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.performance.AppStartMetrics
import io.sentry.android.replay.ReplayCache.Companion.ONGOING_SEGMENT
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_BIT_RATE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_FRAME_RATE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_HEIGHT
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_ID
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_TYPE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_TIMESTAMP
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_WIDTH
import io.sentry.android.replay.util.ReplayShadowMediaCodec
import io.sentry.cache.PersistingOptionsObserver.OPTIONS_CACHE
import io.sentry.cache.PersistingOptionsObserver.REPLAY_ERROR_SAMPLE_RATE_FILENAME
import io.sentry.protocol.Contexts
import io.sentry.protocol.SentryId
import io.sentry.rrweb.RRWebMetaEvent
import io.sentry.rrweb.RRWebVideoEvent
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowActivityManager
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [30],
    shadows = [ReplayShadowMediaCodec::class]
)
class AnrWithReplayIntegrationTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    private class Fixture {
        lateinit var shadowActivityManager: ShadowActivityManager

        fun addAppExitInfo(
            reason: Int? = ApplicationExitInfo.REASON_ANR,
            timestamp: Long? = null,
            importance: Int? = null
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
            shadowActivityManager.addApplicationExitInfo(exitInfo)
        }

        fun prefillOptionsCache(cacheDir: String) {
            val optionsDir = File(cacheDir, OPTIONS_CACHE).also { it.mkdirs() }
            File(optionsDir, REPLAY_ERROR_SAMPLE_RATE_FILENAME).writeText("\"1.0\"")
        }
    }

    private val fixture = Fixture()
    private lateinit var context: Context

    @BeforeTest
    fun `set up`() {
        ReplayShadowMediaCodec.framesToEncode = 5
        Sentry.close()
        AppStartMetrics.getInstance().clear()
        context = ApplicationProvider.getApplicationContext()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        fixture.shadowActivityManager = Shadow.extract(activityManager)
    }

    @Test
    fun `replay is being captured for ANRs in buffer mode`() {
        ReplayShadowMediaCodec.framesToEncode = 1

        val cacheDir = tmpDir.newFolder().absolutePath
        val oneDayAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        fixture.addAppExitInfo(timestamp = oneDayAgo)
        val asserted = AtomicBoolean(false)

        val replayId1 = SentryId()
        val replayId2 = SentryId()

        SentryAndroid.init(context) {
            it.dsn = "https://key@sentry.io/123"
            it.cacheDirPath = cacheDir
            it.isDebug = true
            it.setLogger(SystemOutLogger())
            it.experimental.sessionReplay.onErrorSampleRate = 1.0
            // beforeSend is called after event processors are applied, so we can assert here
            // against the enriched ANR event
            it.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                assertEquals(replayId2.toString(), event.contexts[Contexts.REPLAY_ID])
                event
            }
            it.addEventProcessor(object : EventProcessor {
                override fun process(event: SentryReplayEvent, hint: Hint): SentryReplayEvent {
                    assertEquals(replayId2, event.replayId)
                    assertEquals(ReplayType.BUFFER, event.replayType)
                    assertEquals("0.mp4", event.videoFile?.name)

                    val metaEvents =
                        hint.replayRecording?.payload?.filterIsInstance<RRWebMetaEvent>()
                    assertEquals(912, metaEvents?.first()?.height)
                    assertEquals(416, metaEvents?.first()?.width) // clamped to power of 16

                    val videoEvents =
                        hint.replayRecording?.payload?.filterIsInstance<RRWebVideoEvent>()
                    assertEquals(912, videoEvents?.first()?.height)
                    assertEquals(416, videoEvents?.first()?.width) // clamped to power of 16
                    assertEquals(1000, videoEvents?.first()?.durationMs)
                    assertEquals(1, videoEvents?.first()?.frameCount)
                    assertEquals(1, videoEvents?.first()?.frameRate)
                    assertEquals(0, videoEvents?.first()?.segmentId)
                    asserted.set(true)
                    return event
                }
            })

            // have to do it after the cacheDir is set to options, because it adds a dsn hash after
            fixture.prefillOptionsCache(it.cacheDirPath!!)

            val replayFolder1 = File(it.cacheDirPath!!, "replay_$replayId1").also { it.mkdirs() }
            val replayFolder2 = File(it.cacheDirPath!!, "replay_$replayId2").also { it.mkdirs() }

            File(replayFolder2, ONGOING_SEGMENT).also { file ->
                file.writeText(
                    """
                    $SEGMENT_KEY_HEIGHT=912
                    $SEGMENT_KEY_WIDTH=416
                    $SEGMENT_KEY_FRAME_RATE=1
                    $SEGMENT_KEY_BIT_RATE=75000
                    $SEGMENT_KEY_ID=0
                    $SEGMENT_KEY_TIMESTAMP=2024-07-11T10:25:21.454Z
                    $SEGMENT_KEY_REPLAY_TYPE=BUFFER
                    """.trimIndent()
                )
            }

            val screenshot = File(replayFolder2, "1720693523997.jpg").also { it.createNewFile() }
            screenshot.outputStream().use { os ->
                Bitmap.createBitmap(1, 1, ARGB_8888).compress(JPEG, 80, os)
                os.flush()
            }

            replayFolder1.setLastModified(oneDayAgo - 1000)
            replayFolder2.setLastModified(oneDayAgo - 500)
        }

        await.withAlias("Failed because of BeforeSend callback above, but we swallow BeforeSend exceptions, hence the timeout")
            .untilTrue(asserted)
    }
}
