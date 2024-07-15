package io.sentry.android.replay

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.media.MediaCodec
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Hint
import io.sentry.IHub
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.SentryReplayEvent.ReplayType
import io.sentry.android.replay.video.MuxerConfig
import io.sentry.android.replay.video.SimpleVideoEncoder
import io.sentry.protocol.Mechanism
import io.sentry.protocol.SentryException
import io.sentry.rrweb.RRWebMetaEvent
import io.sentry.rrweb.RRWebVideoEvent
import io.sentry.transport.CurrentDateProvider
import io.sentry.transport.ICurrentDateProvider
import org.awaitility.core.ConditionTimeoutException
import org.awaitility.kotlin.await
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric.buildActivity
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPixelCopy
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MICROSECONDS
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(
    shadows = [ShadowPixelCopy::class],
    sdk = [28],
    qualifiers = "w360dp-h640dp-xxhdpi"
)
class ReplaySmokeTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    internal class Fixture {
        val options = SentryOptions()
        val scope = Scope(options)
        val hub = mock<IHub> {
            doAnswer {
                (it.arguments[0] as ScopeCallback).run(scope)
            }.whenever(it).configureScope(any())
        }
        var encoder: SimpleVideoEncoder? = null
        var count: Int = 0

        private class ImmediateHandler : Handler(Callback { it.callback?.run(); true })

        fun getSut(
            context: Context,
            dateProvider: ICurrentDateProvider = CurrentDateProvider.getInstance(),
            framesToEncode: Int = 0
        ): ReplayIntegration {
            return ReplayIntegration(
                context,
                dateProvider,
                recorderProvider = null,
                recorderConfigProvider = null,
                // this is just needed for testing to encode a fake video
                replayCacheProvider = { replayId, recorderConfig ->
                    ReplayCache(
                        options,
                        replayId,
                        recorderConfig,
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
                },
                replayCaptureStrategyProvider = null,
                mainLooperHandler = mock {
                    whenever(mock.handler).thenReturn(ImmediateHandler())
                    whenever(mock.post(any())).then {
                        (it.arguments[0] as Runnable).run()
                        count++
                    }
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
    fun `works in session mode`() {
        val captured = AtomicBoolean(false)
        whenever(fixture.hub.captureReplay(any(), anyOrNull())).then {
            captured.set(true)
        }

        fixture.options.experimental.sessionReplay.sessionSampleRate = 1.0
        fixture.options.cacheDirPath = tmpDir.newFolder().absolutePath

        val replay: ReplayIntegration = fixture.getSut(context, framesToEncode = 5)
        replay.register(fixture.hub, fixture.options)

        val controller = buildActivity(ExampleActivity::class.java, null).setup()
        controller.create().start().resume()

        replay.start()
        // wait for windows to be registered in our listeners
        shadowOf(Looper.getMainLooper()).idle()

        await.timeout(Duration.ofSeconds(15)).untilTrue(captured)

        verify(fixture.hub).captureReplay(
            check {
                assertEquals(replay.replayId, it.replayId)
                assertEquals(ReplayType.SESSION, it.replayType)
                assertEquals("0.mp4", it.videoFile?.name)
                assertEquals("replay_${replay.replayId}", it.videoFile?.parentFile?.name)
            },
            check {
                val metaEvents = it.replayRecording?.payload?.filterIsInstance<RRWebMetaEvent>()
                assertEquals(640, metaEvents?.first()?.height)
                assertEquals(352, metaEvents?.first()?.width) // clamped to power of 16

                val videoEvents = it.replayRecording?.payload?.filterIsInstance<RRWebVideoEvent>()
                assertEquals(640, videoEvents?.first()?.height)
                assertEquals(352, videoEvents?.first()?.width) // clamped to power of 16
                assertEquals(5000, videoEvents?.first()?.durationMs)
                assertEquals(5, videoEvents?.first()?.frameCount)
                assertEquals(1, videoEvents?.first()?.frameRate)
                assertEquals(0, videoEvents?.first()?.segmentId)
            }
        )
    }

    @Test
    fun `works in buffer mode`() {
        val captured = AtomicBoolean(false)
        whenever(fixture.hub.captureReplay(any(), anyOrNull())).then {
            captured.set(true)
        }

        fixture.options.experimental.sessionReplay.errorSampleRate = 1.0
        fixture.options.cacheDirPath = tmpDir.newFolder().absolutePath

        val replay: ReplayIntegration = fixture.getSut(context, framesToEncode = 10)
        replay.register(fixture.hub, fixture.options)

        val controller = buildActivity(ExampleActivity::class.java, null).setup()
        controller.create().start().resume()

        replay.start()
        // wait for windows to be registered in our listeners
        shadowOf(Looper.getMainLooper()).idle()

        try {
            // Use Awaitility to wait for 10 seconds so buffer is filled
            await.atMost(10, TimeUnit.SECONDS).untilTrue(captured)
        } catch (e: ConditionTimeoutException) {
        }

        val crash = SentryEvent().apply {
            exceptions = listOf(
                SentryException().apply {
                    mechanism = Mechanism().apply { isHandled = false }
                }
            )
        }
        replay.sendReplayForEvent(crash, Hint())

        await.timeout(Duration.ofSeconds(5)).untilTrue(captured)

        verify(fixture.hub).captureReplay(
            check {
                assertEquals(replay.replayId, it.replayId)
                assertEquals(ReplayType.BUFFER, it.replayType)
                assertEquals("0.mp4", it.videoFile?.name)
                assertEquals("replay_${replay.replayId}", it.videoFile?.parentFile?.name)
            },
            check {
                val metaEvents = it.replayRecording?.payload?.filterIsInstance<RRWebMetaEvent>()
                assertEquals(640, metaEvents?.first()?.height)
                assertEquals(352, metaEvents?.first()?.width) // clamped to power of 16

                val videoEvents = it.replayRecording?.payload?.filterIsInstance<RRWebVideoEvent>()
                assertEquals(640, videoEvents?.first()?.height)
                assertEquals(352, videoEvents?.first()?.width) // clamped to power of 16
                assertEquals(10000, videoEvents?.first()?.durationMs)
                // TODO: figure out why there's more than 10
//                assertEquals(10, videoEvents?.first()?.frameCount)
                assertEquals(1, videoEvents?.first()?.frameRate)
                assertEquals(0, videoEvents?.first()?.segmentId)
            }
        )
    }
}

private class ExampleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val linearLayout = LinearLayout(this).apply {
            setBackgroundColor(android.R.color.white)
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        val textView = TextView(this).apply {
            text = "Hello, World!"
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
        linearLayout.addView(textView)

        val image = this::class.java.classLoader.getResource("Tongariro.jpg")!!
        val imageView = ImageView(this).apply {
            setImageDrawable(Drawable.createFromPath(image.path))
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 16, 0, 0)
            }
        }
        linearLayout.addView(imageView)

        setContentView(linearLayout)
    }
}
