package io.sentry.android.replay

import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import android.media.MediaCodec
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.SentryOptions
import io.sentry.android.replay.video.MuxerConfig
import io.sentry.android.replay.video.SimpleVideoEncoder
import io.sentry.protocol.SentryId
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit.MICROSECONDS
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.test.Test
import kotlin.test.assertEquals
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
            val recorderConfig = ScreenshotRecorderConfig(100, 200, 1f, frameRate)
            options.run {
                cacheDirPath = dir?.newFolder()?.absolutePath
            }
            return ReplayCache(options, replayId, recorderConfig, encoderCreator = { videoFile ->
                encoder = SimpleVideoEncoder(
                    options,
                    MuxerConfig(
                        file = videoFile,
                        recorderConfig = recorderConfig,
                        frameRate = recorderConfig.frameRate.toFloat(),
                        bitrate = 20 * 1000
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

        val video = replayCache.createVideoOf(5000L, 0, 0)

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

        replayCache.createVideoOf(3000L, 0, 0)
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

        val segment0 = replayCache.createVideoOf(5000L, 0, 0)
        assertEquals(5, segment0!!.frameCount)
        assertEquals(5000, segment0.duration)
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

        val segment0 = replayCache.createVideoOf(5000L, 0, 0)
        assertEquals(5, segment0!!.frameCount)
        assertEquals(5000, segment0.duration)
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

        val segment0 = replayCache.createVideoOf(5000L, 0, 0)
        assertEquals(5, segment0!!.frameCount)
        assertEquals(5000, segment0.duration)
        assertEquals(File(replayCache.replayCacheDir, "0.mp4"), segment0.video)

        val segment1 = replayCache.createVideoOf(5000L, 5000L, 1)
        assertEquals(5, segment1!!.frameCount)
        assertEquals(5000, segment1.duration)
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

        val segment0 = replayCache.createVideoOf(3000L, 0, 0)
        assertEquals(6, segment0!!.frameCount)
        assertEquals(3000, segment0.duration)
        assertEquals(File(replayCache.replayCacheDir, "0.mp4"), segment0.video)
    }
}
