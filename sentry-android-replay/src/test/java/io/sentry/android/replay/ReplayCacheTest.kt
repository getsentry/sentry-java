package io.sentry.android.replay

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat.JPEG
import android.graphics.Bitmap.Config.ARGB_8888
import android.media.MediaCodec
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.DateUtils
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
import io.sentry.android.replay.video.MuxerConfig
import io.sentry.android.replay.video.SimpleVideoEncoder
import io.sentry.protocol.SentryId
import io.sentry.rrweb.RRWebInteractionEvent
import io.sentry.rrweb.RRWebInteractionEvent.InteractionType.TouchEnd
import io.sentry.rrweb.RRWebInteractionEvent.InteractionType.TouchStart
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit.MICROSECONDS
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [26])
class ReplayCacheTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    internal class Fixture {
        val options = SentryOptions()
        var encoder: SimpleVideoEncoder? = null
        fun getSut(
            dir: TemporaryFolder?,
            replayId: SentryId = SentryId(),
            frameRate: Int,
            framesToEncode: Int = 0
        ): ReplayCache {
            val recorderConfig = ScreenshotRecorderConfig(100, 200, 1f, 1f, frameRate = frameRate, bitRate = 20_000)
            options.run {
                cacheDirPath = dir?.newFolder()?.absolutePath
            }
            return ReplayCache(options, replayId, recorderConfig, encoderProvider = { videoFile, height, width ->
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
                        encodeFrame(framesToEncode, frameRate, size = 0, flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    }
                ).also { it.start() }
                repeat(framesToEncode) { encodeFrame(it, frameRate) }

                encoder!!
            })
        }

        fun encodeFrame(index: Int, frameRate: Int, size: Int = 10, flags: Int = 0) {
            val presentationTime = MICROSECONDS.convert(index * (1000L / frameRate), MILLISECONDS)
            encoder!!.mediaCodec.dequeueInputBuffer(0)
            encoder!!.mediaCodec.queueInputBuffer(index, index * size, size, presentationTime, flags)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when no cacheDirPath specified, does not store screenshots`() {
        val replayId = SentryId()
        val replayCache = fixture.getSut(
            null,
            replayId,
            frameRate = 1
        )

        val bitmap = Bitmap.createBitmap(1, 1, ARGB_8888)
        replayCache.addFrame(bitmap, 1)

        assertTrue(replayCache.frames.isEmpty())
    }

    @Test
    fun `stores screenshots with timestamp as name`() {
        val replayId = SentryId()
        val replayCache = fixture.getSut(
            tmpDir,
            replayId,
            frameRate = 1
        )

        val bitmap = Bitmap.createBitmap(1, 1, ARGB_8888)
        replayCache.addFrame(bitmap, 1)

        val expectedScreenshotFile = File(replayCache.replayCacheDir, "1.jpg")
        assertTrue(expectedScreenshotFile.exists())
        assertEquals(replayCache.frames.first().timestamp, 1)
        assertEquals(replayCache.frames.first().screenshot, expectedScreenshotFile)
    }

    @Test
    fun `when no frames are provided, returns nothing`() {
        val replayCache = fixture.getSut(
            tmpDir,
            frameRate = 1
        )

        val video = replayCache.createVideoOf(5000L, 0, 0, 100, 200)

        assertNull(video)
    }

    @Test
    fun `deletes frames after creating a video`() {
        val replayCache = fixture.getSut(
            tmpDir,
            frameRate = 1,
            framesToEncode = 3
        )

        val bitmap = Bitmap.createBitmap(1, 1, ARGB_8888)
        replayCache.addFrame(bitmap, 1)
        replayCache.addFrame(bitmap, 1001)
        replayCache.addFrame(bitmap, 2001)

        val segment0 = replayCache.createVideoOf(3000L, 0, 0, 100, 200)
        assertEquals(3, segment0!!.frameCount)
        assertEquals(3000, segment0.duration)
        assertTrue { segment0.video.exists() && segment0.video.length() > 0 }
        assertEquals(File(replayCache.replayCacheDir, "0.mp4"), segment0.video)

        assertTrue(replayCache.frames.isEmpty())
        assertTrue(replayCache.replayCacheDir!!.listFiles()!!.none { it.extension == "jpg" })
    }

    @Test
    fun `repeats last known frame for the segment duration`() {
        val replayCache = fixture.getSut(
            tmpDir,
            frameRate = 1,
            framesToEncode = 5
        )

        val bitmap = Bitmap.createBitmap(1, 1, ARGB_8888)
        replayCache.addFrame(bitmap, 1)

        val segment0 = replayCache.createVideoOf(5000L, 0, 0, 100, 200)
        assertEquals(5, segment0!!.frameCount)
        assertEquals(5000, segment0.duration)
        assertTrue { segment0.video.exists() && segment0.video.length() > 0 }
        assertEquals(File(replayCache.replayCacheDir, "0.mp4"), segment0.video)
    }

    @Test
    fun `repeats last known frame for the segment duration for each timespan`() {
        val replayCache = fixture.getSut(
            tmpDir,
            frameRate = 1,
            framesToEncode = 5
        )

        val bitmap = Bitmap.createBitmap(1, 1, ARGB_8888)
        replayCache.addFrame(bitmap, 1)
        replayCache.addFrame(bitmap, 3001)

        val segment0 = replayCache.createVideoOf(5000L, 0, 0, 100, 200)
        assertEquals(5, segment0!!.frameCount)
        assertEquals(5000, segment0.duration)
        assertTrue { segment0.video.exists() && segment0.video.length() > 0 }
        assertEquals(File(replayCache.replayCacheDir, "0.mp4"), segment0.video)
    }

    @Test
    fun `repeats last known frame for each segment`() {
        val replayCache = fixture.getSut(
            tmpDir,
            frameRate = 1,
            framesToEncode = 5
        )

        val bitmap = Bitmap.createBitmap(1, 1, ARGB_8888)
        replayCache.addFrame(bitmap, 1)
        replayCache.addFrame(bitmap, 5001)

        val segment0 = replayCache.createVideoOf(5000L, 0, 0, 100, 200)
        assertEquals(5, segment0!!.frameCount)
        assertEquals(5000, segment0.duration)
        assertEquals(File(replayCache.replayCacheDir, "0.mp4"), segment0.video)

        val segment1 = replayCache.createVideoOf(5000L, 5000L, 1, 100, 200)
        assertEquals(5, segment1!!.frameCount)
        assertEquals(5000, segment1.duration)
        assertTrue { segment0.video.exists() && segment0.video.length() > 0 }
        assertEquals(File(replayCache.replayCacheDir, "1.mp4"), segment1.video)
    }

    @Test
    fun `respects frameRate`() {
        val replayCache = fixture.getSut(
            tmpDir,
            frameRate = 2,
            framesToEncode = 6
        )

        val bitmap = Bitmap.createBitmap(1, 1, ARGB_8888)
        replayCache.addFrame(bitmap, 1)
        replayCache.addFrame(bitmap, 1001)
        replayCache.addFrame(bitmap, 1501)

        val segment0 = replayCache.createVideoOf(3000L, 0, 0, 100, 200)
        assertEquals(6, segment0!!.frameCount)
        assertEquals(3000, segment0.duration)
        assertTrue { segment0.video.exists() && segment0.video.length() > 0 }
        assertEquals(File(replayCache.replayCacheDir, "0.mp4"), segment0.video)
    }

    @Test
    fun `addFrame with File path works`() {
        val replayCache = fixture.getSut(
            tmpDir,
            frameRate = 1,
            framesToEncode = 5
        )

        val flutterCacheDir =
            File(fixture.options.cacheDirPath!!, "flutter_replay").also { it.mkdirs() }
        val screenshot = File(flutterCacheDir, "1.jpg").also { it.createNewFile() }
        val video = File(flutterCacheDir, "flutter_0.mp4")

        screenshot.outputStream().use {
            Bitmap.createBitmap(1, 1, ARGB_8888).compress(JPEG, 80, it)
            it.flush()
        }
        replayCache.addFrame(screenshot, frameTimestamp = 1)

        val segment0 = replayCache.createVideoOf(5000L, 0, 0, 100, 200, videoFile = video)
        assertEquals(5, segment0!!.frameCount)
        assertEquals(5000, segment0.duration)

        assertTrue { segment0.video.exists() && segment0.video.length() > 0 }
        assertEquals(File(flutterCacheDir, "flutter_0.mp4"), segment0.video)
    }

    @Test
    fun `rotates frames`() {
        val replayCache = fixture.getSut(
            tmpDir,
            frameRate = 1,
            framesToEncode = 5
        )

        val bitmap = Bitmap.createBitmap(1, 1, ARGB_8888)
        replayCache.addFrame(bitmap, 1)
        replayCache.addFrame(bitmap, 1001)
        replayCache.addFrame(bitmap, 2001)

        replayCache.rotate(2000)

        assertEquals(1, replayCache.frames.size)
        assertTrue(replayCache.replayCacheDir!!.listFiles()!!.none { it.name == "1.jpg" || it.name == "1001.jpg" })
    }

    @Test
    fun `does not persist segment if already closed`() {
        val replayId = SentryId()
        val replayCache = fixture.getSut(
            tmpDir,
            replayId,
            frameRate = 1
        )

        replayCache.close()

        replayCache.persistSegmentValues("key", "value")
        assertFalse(File(replayCache.replayCacheDir, ONGOING_SEGMENT).exists())
    }

    @Test
    fun `stores segment key value pairs`() {
        val replayId = SentryId()
        val replayCache = fixture.getSut(
            tmpDir,
            replayId,
            frameRate = 1
        )

        replayCache.persistSegmentValues("key1", "value1")
        replayCache.persistSegmentValues("key2", "value2")

        val segmentValues = File(replayCache.replayCacheDir, ONGOING_SEGMENT).readLines()
        assertEquals("key1=value1", segmentValues[0])
        assertEquals("key2=value2", segmentValues[1])
    }

    @Test
    fun `removes segment key value pair, if the value is null`() {
        val replayId = SentryId()
        val replayCache = fixture.getSut(
            tmpDir,
            replayId,
            frameRate = 1
        )

        replayCache.persistSegmentValues("key1", "value1")
        replayCache.persistSegmentValues("key2", "value2")

        replayCache.persistSegmentValues("key1", null)

        val segmentValues = File(replayCache.replayCacheDir, ONGOING_SEGMENT).readLines()
        assertEquals(1, segmentValues.size)
        assertEquals("key2=value2", segmentValues[0])
    }

    @Test
    fun `if no ongoing_segment file exists, deletes replay folder`() {
        fixture.options.run {
            cacheDirPath = tmpDir.newFolder()?.absolutePath
        }
        val replayId = SentryId()
        val replayCacheFolder = File(fixture.options.cacheDirPath!!, "replay_$replayId")
        val lastSegment = ReplayCache.fromDisk(fixture.options, replayId)

        assertNull(lastSegment)
        assertFalse(replayCacheFolder.exists())
    }

    @Test
    fun `if one of the required segment values is not present, deletes replay folder`() {
        fixture.options.run {
            cacheDirPath = tmpDir.newFolder()?.absolutePath
        }
        val replayId = SentryId()
        val replayCacheFolder = File(fixture.options.cacheDirPath!!, "replay_$replayId").also { it.mkdirs() }
        File(replayCacheFolder, ONGOING_SEGMENT).also {
            it.writeText(
                """
                $SEGMENT_KEY_HEIGHT=912
                $SEGMENT_KEY_WIDTH=416
                $SEGMENT_KEY_FRAME_RATE=1
                $SEGMENT_KEY_BIT_RATE=75000
                $SEGMENT_KEY_ID=0
                $SEGMENT_KEY_TIMESTAMP=2024-07-11T10:25:21.454Z
                """.trimIndent()
            )
            // omitting replay type, which is required, for the test
        }

        val lastSegment = ReplayCache.fromDisk(fixture.options, replayId)

        assertNull(lastSegment)
        assertFalse(replayCacheFolder.exists())
    }

    @Test
    fun `returns last segment data when all values are present`() {
        fixture.options.run {
            cacheDirPath = tmpDir.newFolder()?.absolutePath
        }
        val replayId = SentryId()
        val replayCacheFolder = File(fixture.options.cacheDirPath!!, "replay_$replayId").also { it.mkdirs() }
        File(replayCacheFolder, ONGOING_SEGMENT).also {
            it.writeText(
                """
                $SEGMENT_KEY_HEIGHT=912
                $SEGMENT_KEY_WIDTH=416
                $SEGMENT_KEY_FRAME_RATE=1
                $SEGMENT_KEY_BIT_RATE=75000
                $SEGMENT_KEY_ID=0
                $SEGMENT_KEY_TIMESTAMP=2024-07-11T10:25:21.454Z
                $SEGMENT_KEY_REPLAY_TYPE=SESSION
                $SEGMENT_KEY_REPLAY_RECORDING={}[{"type":3,"timestamp":1720693523997,"data":{"source":2,"type":7,"id":0,"x":314.2979431152344,"y":625.44140625,"pointerType":2,"pointerId":0}},{"type":3,"timestamp":1720693524774,"data":{"source":2,"type":9,"id":0,"x":322.00390625,"y":424.4384765625,"pointerType":2,"pointerId":0}}]
                """.trimIndent()
            )
        }

        val lastSegment = ReplayCache.fromDisk(fixture.options, replayId)!!

        assertEquals(912, lastSegment.recorderConfig.recordingHeight)
        assertEquals(416, lastSegment.recorderConfig.recordingWidth)
        assertEquals(1, lastSegment.recorderConfig.frameRate)
        assertEquals(75000, lastSegment.recorderConfig.bitRate)
        assertEquals(0, lastSegment.id)
        assertEquals("2024-07-11T10:25:21.454Z", DateUtils.getTimestamp(lastSegment.timestamp))
        assertEquals(ReplayType.SESSION, lastSegment.replayType)
        assertEquals(fixture.options.experimental.sessionReplay.sessionSegmentDuration, lastSegment.duration)
        assertTrue {
            val firstEvent = lastSegment.events.first() as RRWebInteractionEvent
            firstEvent.timestamp == 1720693523997 &&
                firstEvent.interactionType == TouchStart &&
                firstEvent.x.toDouble() == 314.2979431152344 &&
                firstEvent.y.toDouble() == 625.44140625
        }
        assertTrue {
            val lastEvent = lastSegment.events.last() as RRWebInteractionEvent
            lastEvent.timestamp == 1720693524774 &&
                lastEvent.interactionType == TouchEnd &&
                lastEvent.x.toDouble() == 322.00390625 &&
                lastEvent.y.toDouble() == 424.4384765625
        }
    }

    @Test
    fun `fills in cache with frames from disk`() {
        fixture.options.run {
            cacheDirPath = tmpDir.newFolder()?.absolutePath
        }
        val replayId = SentryId()
        val replayCacheFolder = File(fixture.options.cacheDirPath!!, "replay_$replayId").also { it.mkdirs() }
        File(replayCacheFolder, ONGOING_SEGMENT).also {
            it.writeText(
                """
                $SEGMENT_KEY_HEIGHT=912
                $SEGMENT_KEY_WIDTH=416
                $SEGMENT_KEY_FRAME_RATE=1
                $SEGMENT_KEY_BIT_RATE=75000
                $SEGMENT_KEY_ID=0
                $SEGMENT_KEY_TIMESTAMP=2024-07-11T10:25:21.454Z
                $SEGMENT_KEY_REPLAY_TYPE=SESSION
                """.trimIndent()
            )
        }

        val screenshot = File(replayCacheFolder, "1.jpg").also { it.createNewFile() }
        screenshot.outputStream().use {
            Bitmap.createBitmap(1, 1, ARGB_8888).compress(JPEG, 80, it)
            it.flush()
        }

        val lastSegment = ReplayCache.fromDisk(fixture.options, replayId)!!

        assertEquals(1, lastSegment.cache.frames.size)
        assertEquals(1, lastSegment.cache.frames.first().timestamp)
        assertEquals("1.jpg", lastSegment.cache.frames.first().screenshot.name)
    }
}
