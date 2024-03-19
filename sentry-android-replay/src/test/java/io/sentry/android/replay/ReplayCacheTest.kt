package io.sentry.android.replay

import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.SentryOptions
import io.sentry.protocol.SentryId
import io.sentry.transport.ICurrentDateProvider
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.Test

@RunWith(AndroidJUnit4::class)
@Config(sdk = [26])
class ReplayCacheTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    internal class Fixture {
        val options = SentryOptions()
        fun getSut(
            dir: TemporaryFolder?,
            replayId: SentryId,
            frameRate: Int,
            dateProvider: ICurrentDateProvider
        ): ReplayCache {
            val recorderConfig = ScreenshotRecorderConfig(100, 200, 1f, frameRate)
            options.run {
                cacheDirPath = dir?.newFolder()?.absolutePath
            }
            return ReplayCache(options, replayId, recorderConfig, dateProvider)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `test`() {
        val replayId = SentryId()
        val replayCache = fixture.getSut(
            tmpDir,
            replayId,
            frameRate = 1,
            dateProvider = object : ICurrentDateProvider {
                override fun getCurrentTimeMillis(): Long {
                    return 1
                }
            }
        )

        val bitmap = Bitmap.createBitmap(1, 1, ARGB_8888)
        replayCache.addFrame(bitmap)

        replayCache.createVideoOf(5000L, 0, 0)
        replayCache.createVideoOf(5000L, 5000L, 1)
    }

    @Test
    fun `test2`() {
        val replayId = SentryId()
        val replayCache = fixture.getSut(
            tmpDir,
            replayId,
            frameRate = 1,
            dateProvider = object : ICurrentDateProvider {
                var counter = 0
                override fun getCurrentTimeMillis(): Long {
                    return when (counter++) {
                        0 -> 1
                        1 -> 1001
                        else -> 1001
                    }
                }
            }
        )

        val bitmap = Bitmap.createBitmap(1, 1, ARGB_8888)
        replayCache.addFrame(bitmap)
        replayCache.addFrame(bitmap)

        replayCache.createVideoOf(5000L, 0, 0)
    }
}
