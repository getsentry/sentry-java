package io.sentry.android.replay.capture

import android.graphics.Bitmap
import android.view.MotionEvent
import io.sentry.IScopes
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import io.sentry.SentryReplayEvent.ReplayType
import io.sentry.android.replay.DefaultReplayBreadcrumbConverter
import io.sentry.android.replay.GeneratedVideo
import io.sentry.android.replay.ReplayCache
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_ID
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_ID
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_TYPE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_TIMESTAMP
import io.sentry.android.replay.ReplayFrame
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.android.replay.capture.BufferCaptureStrategyTest.Fixture.Companion.VIDEO_DURATION
import io.sentry.protocol.SentryId
import io.sentry.transport.CurrentDateProvider
import io.sentry.transport.ICurrentDateProvider
import io.sentry.util.Random
import org.awaitility.kotlin.await
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BufferCaptureStrategyTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    internal class Fixture {
        companion object {
            const val VIDEO_DURATION = 5000L
        }

        val options = SentryOptions().apply {
            setReplayController(
                mock {
                    on { breadcrumbConverter }.thenReturn(DefaultReplayBreadcrumbConverter())
                }
            )
        }
        val scope = Scope(options)
        val scopes = mock<IScopes> {
            doAnswer {
                (it.arguments[0] as ScopeCallback).run(scope)
            }.whenever(it).configureScope(any())
        }
        var persistedSegment = LinkedHashMap<String, String?>()
        val replayCache = mock<ReplayCache> {
            on { frames }.thenReturn(mutableListOf(ReplayFrame(File("1720693523997.jpg"), 1720693523997)))
            on { persistSegmentValues(any(), anyOrNull()) }.then {
                persistedSegment.put(it.arguments[0].toString(), it.arguments[1]?.toString())
            }
            on { createVideoOf(anyLong(), anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any()) }
                .thenReturn(GeneratedVideo(File("0.mp4"), 5, VIDEO_DURATION))
        }
        val recorderConfig = ScreenshotRecorderConfig(
            recordingWidth = 1080,
            recordingHeight = 1920,
            scaleFactorX = 1f,
            scaleFactorY = 1f,
            frameRate = 1,
            bitRate = 20_000
        )

        fun getSut(
            onErrorSampleRate: Double = 1.0,
            dateProvider: ICurrentDateProvider = CurrentDateProvider.getInstance(),
            replayCacheDir: File? = null
        ): BufferCaptureStrategy {
            replayCacheDir?.let {
                whenever(replayCache.replayCacheDir).thenReturn(it)
            }
            options.run {
                sessionReplay.onErrorSampleRate = onErrorSampleRate
            }
            return BufferCaptureStrategy(
                options,
                scopes,
                dateProvider,
                Random(),
                mock {
                    doAnswer { invocation ->
                        (invocation.arguments[0] as Runnable).run()
                        null
                    }.whenever(it).submit(any<Runnable>())
                }
            ) { _ -> replayCache }
        }

        fun mockedMotionEvent(action: Int): MotionEvent = mock {
            on { actionMasked }.thenReturn(action)
            on { getPointerId(anyInt()) }.thenReturn(0)
            on { findPointerIndex(anyInt()) }.thenReturn(0)
            on { getX(anyInt()) }.thenReturn(1f)
            on { getY(anyInt()) }.thenReturn(1f)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `start does not set replayId on scope for buffered session`() {
        val strategy = fixture.getSut()
        val replayId = SentryId()

        strategy.start(fixture.recorderConfig, 0, replayId)

        assertEquals(SentryId.EMPTY_ID, fixture.scope.replayId)
        assertEquals(replayId, strategy.currentReplayId)
        assertEquals(0, strategy.currentSegment)
    }

    @Test
    fun `start persists segment values`() {
        val strategy = fixture.getSut()
        val replayId = SentryId()

        strategy.start(fixture.recorderConfig, 0, replayId)

        assertEquals("0", fixture.persistedSegment[SEGMENT_KEY_ID])
        assertEquals(replayId.toString(), fixture.persistedSegment[SEGMENT_KEY_REPLAY_ID])
        assertEquals(
            ReplayType.BUFFER.toString(),
            fixture.persistedSegment[SEGMENT_KEY_REPLAY_TYPE]
        )
        assertTrue(fixture.persistedSegment[SEGMENT_KEY_TIMESTAMP]?.isNotEmpty() == true)
    }

    @Test
    fun `pause creates but does not capture current segment`() {
        val strategy = fixture.getSut()
        strategy.start(fixture.recorderConfig, 0, SentryId())

        strategy.pause()

        await.until { strategy.currentSegment == 1 }

        verify(fixture.scopes, never()).captureReplay(any(), any())
        assertEquals(1, strategy.currentSegment)
    }

    @Test
    fun `stop clears replay cache dir`() {
        val replayId = SentryId()
        val currentReplay =
            File(fixture.options.cacheDirPath, "replay_$replayId").also { it.mkdirs() }

        val strategy = fixture.getSut(replayCacheDir = currentReplay)
        strategy.start(fixture.recorderConfig, 0, replayId)

        strategy.stop()

        verify(fixture.scopes, never()).captureReplay(any(), any())

        assertEquals(SentryId.EMPTY_ID, strategy.currentReplayId)
        assertEquals(-1, strategy.currentSegment)
        assertFalse(currentReplay.exists())
        verify(fixture.replayCache).close()
    }

    @Test
    fun `onScreenshotRecorded adds screenshot to cache`() {
        val now =
            System.currentTimeMillis() + (fixture.options.sessionReplay.errorReplayDuration * 5)
        val strategy = fixture.getSut(
            dateProvider = { now }
        )
        strategy.start(fixture.recorderConfig)

        strategy.onScreenshotRecorded(mock<Bitmap>()) { frameTimestamp ->
            assertEquals(now, frameTimestamp)
        }
    }

    @Test
    fun `onScreenshotRecorded rotates screenshots when out of buffer bounds`() {
        val now =
            System.currentTimeMillis() + (fixture.options.sessionReplay.errorReplayDuration * 5)
        val strategy = fixture.getSut(
            dateProvider = { now }
        )
        strategy.start(fixture.recorderConfig)

        strategy.onScreenshotRecorded(mock<Bitmap>()) { frameTimestamp ->
            assertEquals(now, frameTimestamp)
        }
        verify(fixture.replayCache).rotate(eq(now - fixture.options.sessionReplay.errorReplayDuration))
    }

    @Test
    fun `onConfigurationChanged creates new segment and updates config`() {
        val strategy = fixture.getSut()
        strategy.start(fixture.recorderConfig)

        val newConfig = fixture.recorderConfig.copy(recordingHeight = 1080, recordingWidth = 1920)
        strategy.onConfigurationChanged(newConfig)

        await.until { strategy.currentSegment == 1 }

        verify(fixture.scopes, never()).captureReplay(any(), any())
        assertEquals(1, strategy.currentSegment)
    }

    @Test
    fun `convert does nothing when process is terminating`() {
        val strategy = fixture.getSut()
        strategy.start(fixture.recorderConfig)

        strategy.captureReplay(true) {}

        val converted = strategy.convert()
        assertTrue(converted is BufferCaptureStrategy)
    }

    @Test
    fun `convert converts to session strategy and sets replayId to scope`() {
        val strategy = fixture.getSut()
        strategy.start(fixture.recorderConfig)

        val converted = strategy.convert()
        assertTrue(converted is SessionCaptureStrategy)
        assertEquals(strategy.currentReplayId, fixture.scope.replayId)
    }

    @Test
    fun `convert persists buffer replayType when converting to session strategy`() {
        val strategy = fixture.getSut()
        strategy.start(fixture.recorderConfig)

        val converted = strategy.convert()
        assertEquals(
            ReplayType.BUFFER,
            converted.replayType
        )
    }

    @Test
    fun `captureReplay does not replayId to scope when not sampled`() {
        val strategy = fixture.getSut(onErrorSampleRate = 0.0)
        strategy.start(fixture.recorderConfig)

        strategy.captureReplay(false) {}

        assertEquals(SentryId.EMPTY_ID, fixture.scope.replayId)
    }

    @Test
    fun `captureReplay sets replayId to scope and captures buffered segments`() {
        var called = false
        val strategy = fixture.getSut()
        strategy.start(fixture.recorderConfig)
        strategy.pause()

        strategy.captureReplay(false) {
            called = true
        }

        // buffered + current = 2
        verify(fixture.scopes, times(2)).captureReplay(any(), any())
        assertEquals(strategy.currentReplayId, fixture.scope.replayId)
        assertTrue(called)
    }

    @Test
    fun `captureReplay sets new segment timestamp to new strategy after successful creation`() {
        val strategy = fixture.getSut()
        strategy.start(fixture.recorderConfig)
        val oldTimestamp = strategy.segmentTimestamp

        strategy.captureReplay(false) { newTimestamp ->
            assertEquals(oldTimestamp!!.time + VIDEO_DURATION, newTimestamp.time)
        }

        verify(fixture.scopes).captureReplay(any(), any())
    }

    @Test
    fun `replayId should be set and serialized first`() {
        val strategy = fixture.getSut()
        val replayId = SentryId()

        strategy.start(fixture.recorderConfig, 0, replayId)

        assertEquals(
            replayId.toString(),
            fixture.persistedSegment.values.first(),
            "The replayId must be set first, so when we clean up stale replays" +
                "the current replay cache folder is not being deleted."
        )
    }
}
