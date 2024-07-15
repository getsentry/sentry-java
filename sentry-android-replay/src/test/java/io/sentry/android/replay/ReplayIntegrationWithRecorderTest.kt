package io.sentry.android.replay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat.JPEG
import android.graphics.Bitmap.Config.ARGB_8888
import android.media.MediaCodec
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.IHub
import io.sentry.SentryOptions
import io.sentry.SentryReplayEvent.ReplayType
import io.sentry.android.replay.ReplayIntegrationWithRecorderTest.LifecycleState.CLOSED
import io.sentry.android.replay.ReplayIntegrationWithRecorderTest.LifecycleState.INITALIZED
import io.sentry.android.replay.ReplayIntegrationWithRecorderTest.LifecycleState.PAUSED
import io.sentry.android.replay.ReplayIntegrationWithRecorderTest.LifecycleState.RESUMED
import io.sentry.android.replay.ReplayIntegrationWithRecorderTest.LifecycleState.STARTED
import io.sentry.android.replay.ReplayIntegrationWithRecorderTest.LifecycleState.STOPPED
import io.sentry.android.replay.video.MuxerConfig
import io.sentry.android.replay.video.SimpleVideoEncoder
import io.sentry.rrweb.RRWebMetaEvent
import io.sentry.rrweb.RRWebVideoEvent
import io.sentry.transport.CurrentDateProvider
import io.sentry.transport.ICurrentDateProvider
import org.awaitility.kotlin.await
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit.MICROSECONDS
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(sdk = [26])
class ReplayIntegrationWithRecorderTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    internal class Fixture {
        val options = SentryOptions()
        val hub = mock<IHub>()
        var encoder: SimpleVideoEncoder? = null

        fun getSut(
            context: Context,
            recorder: Recorder,
            recorderConfig: ScreenshotRecorderConfig,
            dateProvider: ICurrentDateProvider = CurrentDateProvider.getInstance(),
            framesToEncode: Int = 0
        ): ReplayIntegration {
            return ReplayIntegration(
                context,
                dateProvider,
                recorderProvider = { recorder },
                recorderConfigProvider = { recorderConfig },
                // this is just needed for testing to encode a fake video
                replayCacheProvider = { replayId, config ->
                    ReplayCache(
                        options,
                        replayId,
                        config,
                        encoderProvider = { videoFile, height, width ->
                            encoder = SimpleVideoEncoder(
                                options,
                                MuxerConfig(
                                    file = videoFile,
                                    recordingHeight = height,
                                    recordingWidth = width,
                                    frameRate = recorderConfig.frameRate,
                                    bitRate = recorderConfig.bitRate
                                ),
                                onClose = {
                                    encodeFrame(
                                        framesToEncode,
                                        recorderConfig.frameRate,
                                        size = 0,
                                        flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                }
                            ).also { it.start() }
                            repeat(framesToEncode) { encodeFrame(it, recorderConfig.frameRate) }

                            encoder!!
                        }
                    )
                }
            )
        }

        private fun encodeFrame(index: Int, frameRate: Int, size: Int = 10, flags: Int = 0) {
            val presentationTime = MICROSECONDS.convert(index * (1000L / frameRate), MILLISECONDS)
            encoder!!.mediaCodec.dequeueInputBuffer(0)
            encoder!!.mediaCodec.queueInputBuffer(
                index,
                index * size,
                size,
                presentationTime,
                flags
            )
        }
    }

    private val fixture = Fixture()
    private lateinit var context: Context

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `works with different recorder`() {
        val captured = AtomicBoolean(false)
        whenever(fixture.hub.captureReplay(any(), anyOrNull())).then {
            captured.set(true)
        }
        // fake current time to trigger segment creation, CurrentDateProvider.getInstance() should
        // be used in prod
        val dateProvider = ICurrentDateProvider {
            System.currentTimeMillis() + fixture.options.experimental.sessionReplay.sessionSegmentDuration
        }

        fixture.options.experimental.sessionReplay.sessionSampleRate = 1.0
        fixture.options.cacheDirPath = tmpDir.newFolder().absolutePath

        val replay: ReplayIntegration
        val recorderConfig = ScreenshotRecorderConfig(100, 200, 1f, 1f, 1, 20_000)
        val recorder = object : Recorder {
            var state: LifecycleState = INITALIZED

            override fun start(recorderConfig: ScreenshotRecorderConfig) {
                state = STARTED
            }

            override fun resume() {
                state = RESUMED
            }

            override fun pause() {
                state = PAUSED
            }

            override fun stop() {
                state = STOPPED
            }

            override fun close() {
                state = CLOSED
            }
        }

        replay = fixture.getSut(context, recorder, recorderConfig, dateProvider, framesToEncode = 5)
        replay.register(fixture.hub, fixture.options)

        assertEquals(INITALIZED, recorder.state)

        replay.start()
        assertEquals(STARTED, recorder.state)

        replay.resume()
        assertEquals(RESUMED, recorder.state)

        replay.pause()
        assertEquals(PAUSED, recorder.state)

        replay.stop()
        assertEquals(STOPPED, recorder.state)

        replay.close()
        assertEquals(CLOSED, recorder.state)

        // start again and capture some frames
        replay.start()

        // have to access 'replayCacheDir' after calling replay.start(), BUT can already be accessed
        // inside recorder.start()
        val screenshot = File(replay.replayCacheDir, "1.jpg").also { it.createNewFile() }

        screenshot.outputStream().use {
            Bitmap.createBitmap(1, 1, ARGB_8888).compress(JPEG, 80, it)
            it.flush()
        }
        replay.onScreenshotRecorded(screenshot, frameTimestamp = 1)

        // verify
        await.untilTrue(captured)

        verify(fixture.hub).captureReplay(
            check {
                assertEquals(replay.replayId, it.replayId)
                assertEquals(ReplayType.SESSION, it.replayType)
                assertEquals("0.mp4", it.videoFile?.name)
                assertEquals("replay_${replay.replayId}", it.videoFile?.parentFile?.name)
            },
            check {
                val metaEvents = it.replayRecording?.payload?.filterIsInstance<RRWebMetaEvent>()
                assertEquals(200, metaEvents?.first()?.height)
                assertEquals(100, metaEvents?.first()?.width)

                val videoEvents = it.replayRecording?.payload?.filterIsInstance<RRWebVideoEvent>()
                assertEquals(200, videoEvents?.first()?.height)
                assertEquals(100, videoEvents?.first()?.width)
                assertEquals(5000, videoEvents?.first()?.durationMs)
                assertEquals(5, videoEvents?.first()?.frameCount)
                assertEquals(1, videoEvents?.first()?.frameRate)
                assertEquals(0, videoEvents?.first()?.segmentId)
            }
        )
    }

    enum class LifecycleState {
        INITALIZED,
        STARTED,
        RESUMED,
        PAUSED,
        STOPPED,
        CLOSED
    }
}
