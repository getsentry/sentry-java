package io.sentry.android.replay.capture

import android.graphics.Bitmap
import io.sentry.Breadcrumb
import io.sentry.DateUtils
import io.sentry.Hint
import io.sentry.IHub
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import io.sentry.SentryReplayEvent
import io.sentry.SentryReplayEvent.ReplayType
import io.sentry.android.replay.DefaultReplayBreadcrumbConverter
import io.sentry.android.replay.GeneratedVideo
import io.sentry.android.replay.ReplayCache
import io.sentry.android.replay.ReplayCache.Companion.ONGOING_SEGMENT
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_BIT_RATE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_FRAME_RATE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_HEIGHT
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_ID
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_ID
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_RECORDING
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_TYPE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_TIMESTAMP
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_WIDTH
import io.sentry.android.replay.ReplayFrame
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.cache.PersistingScopeObserver
import io.sentry.protocol.SentryId
import io.sentry.rrweb.RRWebBreadcrumbEvent
import io.sentry.rrweb.RRWebInteractionEvent
import io.sentry.rrweb.RRWebInteractionEvent.InteractionType
import io.sentry.rrweb.RRWebMetaEvent
import io.sentry.rrweb.RRWebVideoEvent
import io.sentry.transport.CurrentDateProvider
import io.sentry.transport.ICurrentDateProvider
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionCaptureStrategyTest {

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
        val hub = mock<IHub> {
            doAnswer {
                (it.arguments[0] as ScopeCallback).run(scope)
            }.whenever(it).configureScope(any())
        }
        var persistedSegment = mutableMapOf<String, String?>()
        val replayCache = mock<ReplayCache> {
            on { frames }.thenReturn(mutableListOf(ReplayFrame(File("1720693523997.jpg"), 1720693523997)))
            on { persistSegmentValues(any(), anyOrNull()) }.then {
                persistedSegment.put(it.arguments[0].toString(), it.arguments[1]?.toString())
            }
            on { createVideoOf(anyLong(), anyLong(), anyInt(), anyInt(), anyInt(), any()) }
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
            dateProvider: ICurrentDateProvider = CurrentDateProvider.getInstance(),
            replayCacheDir: File? = null
        ): SessionCaptureStrategy {
            replayCacheDir?.let {
                whenever(replayCache.replayCacheDir).thenReturn(it)
            }
            return SessionCaptureStrategy(
                options,
                hub,
                dateProvider,
                mock {
                    doAnswer { invocation ->
                        (invocation.arguments[0] as Runnable).run()
                        null
                    }.whenever(it).submit(any<Runnable>())
                }
            ) { _, _ -> replayCache }
        }
    }

    private val fixture = Fixture()

    @Test
    fun `start sets replayId on scope for full session`() {
        val strategy = fixture.getSut()
        val replayId = SentryId()

        strategy.start(fixture.recorderConfig, 0, replayId, false)

        assertEquals(replayId, fixture.scope.replayId)
        assertEquals(replayId, strategy.currentReplayId)
        assertEquals(0, strategy.currentSegment)
    }

    @Test
    fun `start persists segment values`() {
        val strategy = fixture.getSut()
        val replayId = SentryId()

        strategy.start(fixture.recorderConfig, 0, replayId, false)

        assertEquals("0", fixture.persistedSegment[SEGMENT_KEY_ID])
        assertEquals(replayId.toString(), fixture.persistedSegment[SEGMENT_KEY_REPLAY_ID])
        assertEquals(
            ReplayType.SESSION.toString(),
            fixture.persistedSegment[SEGMENT_KEY_REPLAY_TYPE]
        )
        assertEquals(
            fixture.recorderConfig.recordingWidth.toString(),
            fixture.persistedSegment[SEGMENT_KEY_WIDTH]
        )
        assertEquals(
            fixture.recorderConfig.recordingHeight.toString(),
            fixture.persistedSegment[SEGMENT_KEY_HEIGHT]
        )
        assertEquals(
            fixture.recorderConfig.frameRate.toString(),
            fixture.persistedSegment[SEGMENT_KEY_FRAME_RATE]
        )
        assertEquals(
            fixture.recorderConfig.bitRate.toString(),
            fixture.persistedSegment[SEGMENT_KEY_BIT_RATE]
        )
        assertTrue(fixture.persistedSegment[SEGMENT_KEY_TIMESTAMP]?.isNotEmpty() == true)
    }

    @Test
    fun `start cleans up old replays`() {
        val replayId = SentryId()

        fixture.options.cacheDirPath = tmpDir.newFolder().absolutePath
        val currentReplay =
            File(fixture.options.cacheDirPath, "replay_$replayId").also { it.mkdirs() }
        val evenOlderReplay =
            File(fixture.options.cacheDirPath, "replay_${SentryId()}").also { it.mkdirs() }
        val scopeCache = File(
            fixture.options.cacheDirPath,
            PersistingScopeObserver.SCOPE_CACHE
        ).also { it.mkdirs() }

        val strategy = fixture.getSut()

        strategy.start(fixture.recorderConfig, 0, replayId, true)

        // deletes older replay folders, but keeps current and previous replay + everything else
        assertTrue(currentReplay.exists())
        assertTrue(scopeCache.exists())
        assertFalse(evenOlderReplay.exists())
    }

    @Test
    fun `start finalizes previous replay`() {
        val replayId = SentryId()
        val oldReplayId = SentryId()

        fixture.options.cacheDirPath = tmpDir.newFolder().absolutePath
        val currentReplay =
            File(fixture.options.cacheDirPath, "replay_$replayId").also { it.mkdirs() }
        val oldReplay =
            File(fixture.options.cacheDirPath, "replay_$oldReplayId").also { it.mkdirs() }
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

        val strategy = fixture.getSut(replayCacheDir = oldReplay)
        strategy.start(fixture.recorderConfig, 0, replayId, true)

        assertTrue(currentReplay.exists())
        assertFalse(oldReplay.exists())
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
    fun `pause creates and captures current segment`() {
        val strategy = fixture.getSut()
        strategy.start(fixture.recorderConfig, 0, SentryId(), false)

        strategy.pause()

        verify(fixture.hub).captureReplay(
            argThat { event ->
                event is SentryReplayEvent && event.segmentId == 0
            },
            any()
        )
        assertEquals(1, strategy.currentSegment)
    }

    @Test
    fun `stop creates and captures current segment and clears replayId from scope`() {
        val replayId = SentryId()
        val currentReplay =
            File(fixture.options.cacheDirPath, "replay_$replayId").also { it.mkdirs() }

        val strategy = fixture.getSut(replayCacheDir = currentReplay)
        strategy.start(fixture.recorderConfig, 0, replayId, false)

        strategy.stop()

        verify(fixture.hub).captureReplay(
            argThat { event ->
                event is SentryReplayEvent && event.segmentId == 0
            },
            any()
        )
        assertEquals(SentryId.EMPTY_ID, fixture.scope.replayId)
        assertEquals(SentryId.EMPTY_ID, strategy.currentReplayId)
        assertEquals(-1, strategy.currentSegment)
        assertFalse(currentReplay.exists())
        verify(fixture.replayCache).close()
    }

    @Test
    fun `sendReplayForEvent captures last segment for crashed event`() {
        val strategy = fixture.getSut()
        strategy.start(fixture.recorderConfig)

        strategy.sendReplayForEvent(true, "event-id", Hint()) {}

        verify(fixture.hub).captureReplay(
            argThat { event ->
                event is SentryReplayEvent && event.segmentId == 0
            },
            any()
        )
    }

    @Test
    fun `sendReplayForEvent does nothing for non-crashed event`() {
        val strategy = fixture.getSut()
        strategy.start(fixture.recorderConfig)

        strategy.sendReplayForEvent(false, "event-id", Hint()) {}

        verify(fixture.hub, never()).captureReplay(any(), any())
    }

    @Test
    fun `onScreenshotRecorded creates new segment when segment duration exceeded`() {
        val now =
            System.currentTimeMillis() + (fixture.options.experimental.sessionReplay.sessionSegmentDuration * 5)
        val strategy = fixture.getSut(
            dateProvider = { now }
        )
        strategy.start(fixture.recorderConfig)

        strategy.onScreenshotRecorded(mock<Bitmap>()) { frameTimestamp ->
            assertEquals(now, frameTimestamp)
        }

        var segmentTimestamp: Date? = null
        verify(fixture.hub).captureReplay(
            argThat { event ->
                segmentTimestamp = event.replayStartTimestamp
                event is SentryReplayEvent && event.segmentId == 0
            },
            any()
        )
        assertEquals(1, strategy.currentSegment)

        segmentTimestamp!!.time = segmentTimestamp!!.time.plus(Fixture.VIDEO_DURATION)
        // timestamp should be updated with video duration
        assertEquals(
            DateUtils.getTimestamp(segmentTimestamp!!),
            fixture.persistedSegment[SEGMENT_KEY_TIMESTAMP]
        )
    }

    @Test
    fun `onScreenshotRecorded stops replay when replay duration exceeded`() {
        val now =
            System.currentTimeMillis() + (fixture.options.experimental.sessionReplay.sessionDuration * 2)
        var count = 0
        val strategy = fixture.getSut(
            dateProvider = {
                // we only need to fake value for the 3rd call (first two is for replayStartTimestamp and frameTimestamp)
                if (count++ == 2) {
                    now
                } else {
                    System.currentTimeMillis()
                }
            }
        )
        strategy.start(fixture.recorderConfig)

        strategy.onScreenshotRecorded(mock<Bitmap>()) {}

        verify(fixture.options.replayController).stop()
    }

    @Test
    fun `onConfigurationChanged creates new segment and updates config`() {
        val strategy = fixture.getSut()
        strategy.start(fixture.recorderConfig)

        val newConfig = fixture.recorderConfig.copy(recordingHeight = 1080, recordingWidth = 1920)
        strategy.onConfigurationChanged(newConfig)

        var segmentTimestamp: Date? = null
        verify(fixture.hub).captureReplay(
            argThat { event ->
                segmentTimestamp = event.replayStartTimestamp
                event is SentryReplayEvent && event.segmentId == 0
            },
            check {
                val metaEvents = it.replayRecording?.payload?.filterIsInstance<RRWebMetaEvent>()
                // should still capture with the old values
                assertEquals(1920, metaEvents?.first()?.height)
                assertEquals(1080, metaEvents?.first()?.width)
            }
        )
        assertEquals(1, strategy.currentSegment)

        segmentTimestamp!!.time = segmentTimestamp!!.time.plus(Fixture.VIDEO_DURATION)
        assertEquals("1080", fixture.persistedSegment[SEGMENT_KEY_HEIGHT])
        assertEquals("1920", fixture.persistedSegment[SEGMENT_KEY_WIDTH])
        // timestamp should be updated with video duration
        assertEquals(
            DateUtils.getTimestamp(segmentTimestamp!!),
            fixture.persistedSegment[SEGMENT_KEY_TIMESTAMP]
        )
    }

    @Test
    fun `fills replay urls from navigation breadcrumbs`() {
        val now =
            System.currentTimeMillis() + (fixture.options.experimental.sessionReplay.sessionSegmentDuration * 5)
        val strategy = fixture.getSut(dateProvider = { now })
        strategy.start(fixture.recorderConfig)

        fixture.scope.addBreadcrumb(Breadcrumb.navigation("from", "to"))

        strategy.onScreenshotRecorded(mock<Bitmap>()) {}

        verify(fixture.hub).captureReplay(
            check {
                assertEquals("to", it.urls!!.first())
            },
            check {
                val breadcrumbEvents =
                    it.replayRecording?.payload?.filterIsInstance<RRWebBreadcrumbEvent>()
                assertEquals("navigation", breadcrumbEvents?.first()?.category)
                assertEquals("to", breadcrumbEvents?.first()?.data?.get("to"))
            }
        )
    }

    @Test
    fun `sets screen from scope as replay url`() {
        fixture.scope.screen = "MainActivity"

        val now =
            System.currentTimeMillis() + (fixture.options.experimental.sessionReplay.sessionSegmentDuration * 5)
        val strategy = fixture.getSut(dateProvider = { now })
        strategy.start(fixture.recorderConfig)

        strategy.onScreenshotRecorded(mock<Bitmap>()) {}

        verify(fixture.hub).captureReplay(
            check {
                assertEquals("MainActivity", it.urls!!.first())
            },
            check {
                val breadcrumbEvents =
                    it.replayRecording?.payload?.filterIsInstance<RRWebBreadcrumbEvent>()
                assertTrue(breadcrumbEvents?.isEmpty() == true)
            }
        )
    }
}
