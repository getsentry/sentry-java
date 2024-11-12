package io.sentry.android.replay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat.JPEG
import android.graphics.Bitmap.Config.ARGB_8888
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Breadcrumb
import io.sentry.DateUtils
import io.sentry.Hint
import io.sentry.IConnectionStatusProvider.ConnectionStatus.CONNECTED
import io.sentry.IConnectionStatusProvider.ConnectionStatus.DISCONNECTED
import io.sentry.IHub
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryEvent
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryOptions
import io.sentry.SentryReplayEvent.ReplayType
import io.sentry.android.replay.ReplayCache.Companion.ONGOING_SEGMENT
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_BIT_RATE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_FRAME_RATE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_HEIGHT
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_ID
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_RECORDING
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_TYPE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_TIMESTAMP
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_WIDTH
import io.sentry.android.replay.capture.CaptureStrategy
import io.sentry.android.replay.capture.SessionCaptureStrategy
import io.sentry.android.replay.capture.SessionCaptureStrategyTest.Fixture.Companion.VIDEO_DURATION
import io.sentry.android.replay.gestures.GestureRecorder
import io.sentry.cache.PersistingScopeObserver
import io.sentry.protocol.SentryException
import io.sentry.protocol.SentryId
import io.sentry.rrweb.RRWebBreadcrumbEvent
import io.sentry.rrweb.RRWebInteractionEvent
import io.sentry.rrweb.RRWebInteractionEvent.InteractionType
import io.sentry.rrweb.RRWebMetaEvent
import io.sentry.rrweb.RRWebVideoEvent
import io.sentry.transport.CurrentDateProvider
import io.sentry.transport.ICurrentDateProvider
import io.sentry.transport.RateLimiter
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [26])
class ReplayIntegrationTest {
    @get:Rule
    val tmpDir = TemporaryFolder()

    internal class Fixture {
        val options = SentryOptions().apply {
            setReplayController(
                mock {
                    on { breadcrumbConverter }.thenReturn(DefaultReplayBreadcrumbConverter())
                }
            )
            executorService = mock {
                doAnswer {
                    (it.arguments[0] as Runnable).run()
                }.whenever(mock).submit(any<Runnable>())
            }
        }
        val scope = Scope(options)
        val rateLimiter = mock<RateLimiter>()
        val hub = mock<IHub> {
            doAnswer {
                ((it.arguments[0]) as ScopeCallback).run(scope)
            }.whenever(mock).configureScope(any<ScopeCallback>())
            on { rateLimiter }.thenReturn(rateLimiter)
        }

        val replayCache = mock<ReplayCache> {
            on { frames }.thenReturn(mutableListOf(ReplayFrame(File("1720693523997.jpg"), 1720693523997)))
            on { createVideoOf(anyLong(), anyLong(), anyInt(), anyInt(), anyInt(), any()) }
                .thenReturn(GeneratedVideo(File("0.mp4"), 5, VIDEO_DURATION))
        }

        fun getSut(
            context: Context,
            sessionSampleRate: Double = 1.0,
            onErrorSampleRate: Double = 1.0,
            isOffline: Boolean = false,
            isRateLimited: Boolean = false,
            recorderProvider: (() -> Recorder)? = null,
            replayCaptureStrategyProvider: ((isFullSession: Boolean) -> CaptureStrategy)? = null,
            recorderConfigProvider: ((configChanged: Boolean) -> ScreenshotRecorderConfig)? = null,
            gestureRecorderProvider: (() -> GestureRecorder)? = null,
            dateProvider: ICurrentDateProvider = CurrentDateProvider.getInstance()
        ): ReplayIntegration {
            options.run {
                experimental.sessionReplay.onErrorSampleRate = onErrorSampleRate
                experimental.sessionReplay.sessionSampleRate = sessionSampleRate
                connectionStatusProvider = mock {
                    on { connectionStatus }.thenReturn(if (isOffline) DISCONNECTED else CONNECTED)
                }
            }
            if (isRateLimited) {
                whenever(rateLimiter.isActiveForCategory(any())).thenReturn(true)
            }
            return ReplayIntegration(
                context,
                dateProvider,
                recorderProvider,
                recorderConfigProvider = recorderConfigProvider,
                replayCacheProvider = { _, _ -> replayCache },
                replayCaptureStrategyProvider = replayCaptureStrategyProvider,
                gestureRecorderProvider = gestureRecorderProvider
            )
        }
    }

    private val fixture = Fixture()
    private lateinit var context: Context

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
        SentryIntegrationPackageStorage.getInstance().clearStorage()
    }

    @Test
    @Config(sdk = [24])
    fun `when API is below 26, does not register`() {
        val replay = fixture.getSut(context)

        replay.register(fixture.hub, fixture.options)

        assertFalse(replay.isEnabled.get())
    }

    @Test
    fun `when no sample rate is set, does not register`() {
        val replay = fixture.getSut(context, 0.0, 0.0)

        replay.register(fixture.hub, fixture.options)

        assertFalse(replay.isEnabled.get())
    }

    @Test
    fun `registers the integration`() {
        var recorderCreated = false
        val replay = fixture.getSut(context, recorderProvider = {
            recorderCreated = true
            mock()
        })

        replay.register(fixture.hub, fixture.options)

        assertTrue(replay.isEnabled.get())
        assertTrue(recorderCreated)
        assertTrue(SentryIntegrationPackageStorage.getInstance().integrations.contains("Replay"))
    }

    @Test
    fun `when disabled start does nothing`() {
        val captureStrategy = mock<CaptureStrategy>()
        val replay = fixture.getSut(context, replayCaptureStrategyProvider = { captureStrategy })

        replay.start()

        verify(captureStrategy, never()).start(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `start sets isRecording to true`() {
        val captureStrategy = mock<CaptureStrategy>()
        val replay = fixture.getSut(context, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.start()

        assertTrue(replay.isRecording)
    }

    @Test
    fun `starting two times does nothing`() {
        val captureStrategy = mock<CaptureStrategy>()
        val replay = fixture.getSut(context, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.start()
        replay.start()

        verify(captureStrategy, times(1)).start(
            any(),
            eq(0),
            argThat { this != SentryId.EMPTY_ID },
            anyOrNull()
        )
    }

    @Test
    fun `does not start replay when session is not sampled`() {
        val captureStrategy = mock<CaptureStrategy>()
        val replay = fixture.getSut(context, onErrorSampleRate = 0.0, sessionSampleRate = 0.0, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.start()

        verify(captureStrategy, never()).start(
            any(),
            eq(0),
            argThat { this != SentryId.EMPTY_ID },
            anyOrNull()
        )
    }

    @Test
    fun `still starts replay when errorsSampleRate is set`() {
        val captureStrategy = mock<CaptureStrategy>()
        val replay = fixture.getSut(context, sessionSampleRate = 0.0, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.start()

        verify(captureStrategy, times(1)).start(
            any(),
            eq(0),
            argThat { this != SentryId.EMPTY_ID },
            anyOrNull()
        )
    }

    @Test
    fun `calls recorder start`() {
        val recorder = mock<Recorder>()
        val replay = fixture.getSut(context, recorderProvider = { recorder })

        replay.register(fixture.hub, fixture.options)
        replay.start()

        verify(recorder).start(any())
    }

    @Test
    fun `resume does not resume when not recording`() {
        val captureStrategy = mock<CaptureStrategy>()
        val replay = fixture.getSut(context, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.resume()

        verify(captureStrategy, never()).resume()
    }

    @Test
    fun `resume resumes capture strategy and recorder`() {
        val captureStrategy = mock<CaptureStrategy>()
        val recorder = mock<Recorder>()
        val replay = fixture.getSut(context, recorderProvider = { recorder }, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.start()
        replay.resume()

        verify(captureStrategy).resume()
        verify(recorder).resume()
    }

    @Test
    fun `captureReplay does nothing when not recording`() {
        val captureStrategy = mock<CaptureStrategy>()
        val replay = fixture.getSut(context, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)

        val event = SentryEvent().apply {
            exceptions = listOf(SentryException())
        }
        replay.captureReplay(event.isCrashed)

        verify(captureStrategy, never()).captureReplay(any(), any())
    }

    @Test
    fun `captureReplay does nothing when currentReplayId is not set`() {
        val captureStrategy = mock<CaptureStrategy> {
            whenever(mock.currentReplayId).thenReturn(SentryId.EMPTY_ID)
        }
        val replay = fixture.getSut(context, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.start()

        val event = SentryEvent().apply {
            exceptions = listOf(SentryException())
        }
        replay.captureReplay(event.isCrashed)

        verify(captureStrategy, never()).captureReplay(any(), any())
    }

    @Test
    fun `captureReplay calls and converts strategy`() {
        val captureStrategy = mock<CaptureStrategy> {
            whenever(mock.currentReplayId).thenReturn(SentryId())
        }
        val replay = fixture.getSut(context, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.start()

        val id = SentryId()
        val event = SentryEvent().apply {
            exceptions = listOf(SentryException())
        }
        event.eventId = id
        val hint = Hint()
        replay.captureReplay(event.isCrashed)

        verify(captureStrategy).captureReplay(eq(false), any())
        verify(captureStrategy).convert()
    }

    @Test
    fun `pause does nothing when not recording`() {
        val captureStrategy = mock<CaptureStrategy>()
        val replay = fixture.getSut(context, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.pause()

        verify(captureStrategy, never()).pause()
    }

    @Test
    fun `pause calls strategy and recorder`() {
        val captureStrategy = mock<CaptureStrategy>()
        val recorder = mock<Recorder>()
        val replay = fixture.getSut(context, recorderProvider = { recorder }, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.start()
        replay.pause()

        verify(captureStrategy).pause()
        verify(recorder).pause()
    }

    @Test
    fun `stop does nothing when not recording`() {
        val captureStrategy = mock<CaptureStrategy>()
        val recorder = mock<Recorder>()
        val replay = fixture.getSut(context, recorderProvider = { recorder }, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.stop()

        verify(captureStrategy, never()).stop()
        verify(recorder, never()).stop()
    }

    @Test
    fun `stop calls stop for recorders and strategy and sets recording to false`() {
        val captureStrategy = mock<CaptureStrategy>()
        val recorder = mock<Recorder>()
        val gestureRecorder = mock<GestureRecorder>()
        val replay = fixture.getSut(
            context,
            recorderProvider = { recorder },
            replayCaptureStrategyProvider = { captureStrategy },
            gestureRecorderProvider = { gestureRecorder }
        )

        replay.register(fixture.hub, fixture.options)
        replay.start()
        replay.stop()

        verify(captureStrategy).stop()
        verify(recorder).stop()
        verify(gestureRecorder).stop()
        assertFalse(replay.isRecording)
    }

    @Test
    fun `close cleans up resources`() {
        val recorder = mock<Recorder>()
        val captureStrategy = mock<CaptureStrategy>()
        val replay = fixture.getSut(context, recorderProvider = { recorder }, replayCaptureStrategyProvider = { captureStrategy })
        replay.register(fixture.hub, fixture.options)
        replay.start()

        replay.close()

        verify(recorder).stop()
        verify(recorder).close()
        verify(captureStrategy).stop()
        verify(captureStrategy).close()
        assertFalse(replay.isRecording())
    }

    @Test
    fun `onConfigurationChanged does nothing when not recording`() {
        val captureStrategy = mock<CaptureStrategy>()
        val recorder = mock<Recorder>()
        val replay = fixture.getSut(context, recorderProvider = { recorder }, replayCaptureStrategyProvider = { captureStrategy })

        replay.register(fixture.hub, fixture.options)
        replay.onConfigurationChanged(mock())

        verify(captureStrategy, never()).onConfigurationChanged(any())
        verify(recorder, never()).stop()
    }

    @Test
    fun `onConfigurationChanged stops and restarts recorder with a new recorder config`() {
        var configChanged = false
        val recorderConfig = mock<ScreenshotRecorderConfig>()
        val captureStrategy = mock<CaptureStrategy>()
        val recorder = mock<Recorder>()
        val replay = fixture.getSut(
            context,
            recorderProvider = { recorder },
            replayCaptureStrategyProvider = { captureStrategy },
            recorderConfigProvider = { configChanged = it; recorderConfig }
        )

        replay.register(fixture.hub, fixture.options)
        replay.start()
        replay.onConfigurationChanged(mock())

        verify(recorder).stop()
        verify(captureStrategy).onConfigurationChanged(eq(recorderConfig))
        verify(recorder, times(2)).start(eq(recorderConfig))
        assertTrue(configChanged)
    }

    @Test
    fun `register finalizes previous replay`() {
        val oldReplayId = SentryId()

        fixture.options.cacheDirPath = tmpDir.newFolder().absolutePath
        val oldReplay =
            File(fixture.options.cacheDirPath, "replay_$oldReplayId").also { it.mkdirs() }
        val screenshot = File(oldReplay, "1720693523997.jpg").also { it.createNewFile() }
        screenshot.outputStream().use {
            Bitmap.createBitmap(1, 1, ARGB_8888).compress(JPEG, 80, it)
            it.flush()
        }
        val scopeCache = File(
            fixture.options.cacheDirPath,
            PersistingScopeObserver.SCOPE_CACHE
        ).also { it.mkdirs() }
        File(scopeCache, PersistingScopeObserver.REPLAY_FILENAME).also {
            it.createNewFile()
            it.writeText("\"$oldReplayId\"")
        }
        val breadcrumbsFile = File(scopeCache, PersistingScopeObserver.BREADCRUMBS_FILENAME)
        fixture.options.serializer.serialize(
            listOf(
                Breadcrumb(DateUtils.getDateTime("2024-07-11T10:25:23.454Z")).apply {
                    category = "navigation"
                    type = "navigation"
                    setData("from", "from")
                    setData("to", "to")
                }
            ),
            breadcrumbsFile.writer()
        )
        File(oldReplay, ONGOING_SEGMENT).also {
            it.writeText(
                """
                $SEGMENT_KEY_HEIGHT=912
                $SEGMENT_KEY_WIDTH=416
                $SEGMENT_KEY_FRAME_RATE=1
                $SEGMENT_KEY_BIT_RATE=75000
                $SEGMENT_KEY_ID=1
                $SEGMENT_KEY_TIMESTAMP=2024-07-11T10:25:21.454Z
                $SEGMENT_KEY_REPLAY_TYPE=SESSION
                $SEGMENT_KEY_REPLAY_RECORDING={}[{"type":3,"timestamp":1720693523997,"data":{"source":2,"type":7,"id":0,"x":314.2979431152344,"y":625.44140625,"pointerType":2,"pointerId":0}},{"type":3,"timestamp":1720693524774,"data":{"source":2,"type":9,"id":0,"x":322.00390625,"y":424.4384765625,"pointerType":2,"pointerId":0}}]
                """.trimIndent()
            )
        }

        val replay = fixture.getSut(context)
        replay.register(fixture.hub, fixture.options)

        assertTrue(oldReplay.exists()) // should not be deleted until the video is packed into envelope
        verify(fixture.hub).captureReplay(
            check {
                assertEquals(oldReplayId, it.replayId)
                assertEquals(ReplayType.SESSION, it.replayType)
                assertEquals("0.mp4", it.videoFile?.name)
            },
            check {
                val metaEvents = it.replayRecording?.payload?.filterIsInstance<RRWebMetaEvent>()
                assertEquals(912, metaEvents?.first()?.height)
                assertEquals(416, metaEvents?.first()?.width) // clamped to power of 16

                val videoEvents = it.replayRecording?.payload?.filterIsInstance<RRWebVideoEvent>()
                assertEquals(912, videoEvents?.first()?.height)
                assertEquals(416, videoEvents?.first()?.width) // clamped to power of 16
                assertEquals(5000, videoEvents?.first()?.durationMs)
                assertEquals(5, videoEvents?.first()?.frameCount)
                assertEquals(1, videoEvents?.first()?.frameRate)
                assertEquals(1, videoEvents?.first()?.segmentId)

                val breadcrumbEvents =
                    it.replayRecording?.payload?.filterIsInstance<RRWebBreadcrumbEvent>()
                assertEquals("navigation", breadcrumbEvents?.first()?.category)
                assertEquals("to", breadcrumbEvents?.first()?.data?.get("to"))

                val interactionEvents =
                    it.replayRecording?.payload?.filterIsInstance<RRWebInteractionEvent>()
                assertEquals(
                    InteractionType.TouchStart,
                    interactionEvents?.first()?.interactionType
                )
                assertEquals(314.29794f, interactionEvents?.first()?.x)
                assertEquals(625.4414f, interactionEvents?.first()?.y)

                assertEquals(InteractionType.TouchEnd, interactionEvents?.last()?.interactionType)
                assertEquals(322.0039f, interactionEvents?.last()?.x)
                assertEquals(424.43848f, interactionEvents?.last()?.y)
            }
        )
    }

    @Test
    fun `register cleans up old replays`() {
        val replayId = SentryId()

        fixture.options.cacheDirPath = tmpDir.newFolder().absolutePath
        val evenOlderReplay =
            File(fixture.options.cacheDirPath, "replay_${SentryId()}").also { it.mkdirs() }
        val scopeCache = File(
            fixture.options.cacheDirPath,
            PersistingScopeObserver.SCOPE_CACHE
        ).also { it.mkdirs() }

        val captureStrategy = mock<CaptureStrategy> {
            on { currentReplayId }.thenReturn(replayId)
        }
        val replay = fixture.getSut(context, replayCaptureStrategyProvider = { captureStrategy })
        replay.register(fixture.hub, fixture.options)

        assertTrue(scopeCache.exists())
        assertFalse(evenOlderReplay.exists())
    }

    @Test
    fun `onScreenshotRecorded supplies screen from scope to replay cache`() {
        val captureStrategy = mock<CaptureStrategy> {
            doAnswer {
                ((it.arguments[1] as ReplayCache.(frameTimestamp: Long) -> Unit)).invoke(fixture.replayCache, 1720693523997)
            }.whenever(mock).onScreenshotRecorded(anyOrNull<Bitmap>(), any())
        }
        val replay = fixture.getSut(context, replayCaptureStrategyProvider = { captureStrategy })

        fixture.hub.configureScope { it.screen = "MainActivity" }
        replay.register(fixture.hub, fixture.options)
        replay.start()

        replay.onScreenshotRecorded(mock<Bitmap>())

        verify(fixture.replayCache).addFrame(any<Bitmap>(), any(), eq("MainActivity"))
    }

    @Test
    fun `onScreenshotRecorded pauses replay when offline for sessions`() {
        val captureStrategy = SessionCaptureStrategy(fixture.options, null, CurrentDateProvider.getInstance())
        val recorder = mock<Recorder>()
        val replay = fixture.getSut(
            context,
            recorderProvider = { recorder },
            replayCaptureStrategyProvider = { captureStrategy },
            isOffline = true
        )

        replay.register(fixture.hub, fixture.options)
        replay.start()
        replay.onScreenshotRecorded(mock<Bitmap>())

        verify(recorder).pause()
    }

    @Test
    fun `onScreenshotRecorded pauses replay when rate-limited for sessions`() {
        val captureStrategy = SessionCaptureStrategy(fixture.options, null, CurrentDateProvider.getInstance())
        val recorder = mock<Recorder>()
        val replay = fixture.getSut(
            context,
            recorderProvider = { recorder },
            replayCaptureStrategyProvider = { captureStrategy },
            isRateLimited = true
        )

        replay.register(fixture.hub, fixture.options)
        replay.start()
        replay.onScreenshotRecorded(mock<Bitmap>())

        verify(recorder).pause()
    }

    @Test
    fun `onConnectionStatusChanged pauses replay when offline for sessions`() {
        val captureStrategy = SessionCaptureStrategy(fixture.options, null, CurrentDateProvider.getInstance())
        val recorder = mock<Recorder>()
        val replay = fixture.getSut(
            context,
            recorderProvider = { recorder },
            replayCaptureStrategyProvider = { captureStrategy }
        )

        replay.register(fixture.hub, fixture.options)
        replay.start()
        replay.onConnectionStatusChanged(DISCONNECTED)

        verify(recorder).pause()
    }

    @Test
    fun `onConnectionStatusChanged resumes replay when back-online for sessions`() {
        val captureStrategy = SessionCaptureStrategy(fixture.options, null, CurrentDateProvider.getInstance())
        val recorder = mock<Recorder>()
        val replay = fixture.getSut(
            context,
            recorderProvider = { recorder },
            replayCaptureStrategyProvider = { captureStrategy }
        )

        replay.register(fixture.hub, fixture.options)
        replay.start()
        replay.onConnectionStatusChanged(CONNECTED)

        verify(recorder).resume()
    }

    @Test
    fun `onRateLimitChanged pauses replay when rate-limited for sessions`() {
        val captureStrategy = SessionCaptureStrategy(fixture.options, null, CurrentDateProvider.getInstance())
        val recorder = mock<Recorder>()
        val replay = fixture.getSut(
            context,
            recorderProvider = { recorder },
            replayCaptureStrategyProvider = { captureStrategy },
            isRateLimited = true
        )

        replay.register(fixture.hub, fixture.options)
        replay.start()
        replay.onRateLimitChanged(true)

        verify(recorder).pause()
    }

    @Test
    fun `onRateLimitChanged resumes replay when rate-limit lifted for sessions`() {
        val captureStrategy = SessionCaptureStrategy(fixture.options, null, CurrentDateProvider.getInstance())
        val recorder = mock<Recorder>()
        val replay = fixture.getSut(
            context,
            recorderProvider = { recorder },
            replayCaptureStrategyProvider = { captureStrategy },
            isRateLimited = false
        )

        replay.register(fixture.hub, fixture.options)
        replay.start()
        replay.onRateLimitChanged(false)

        verify(recorder).resume()
    }
}
