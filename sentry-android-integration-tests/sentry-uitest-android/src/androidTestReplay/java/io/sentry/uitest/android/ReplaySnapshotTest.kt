package io.sentry.uitest.android

import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import io.sentry.SentryReplayOptions
import io.sentry.TypeCheckHint
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.Before

class ReplaySnapshotTest : BaseUiTest() {

  @Before
  fun setup() {
    // crash on swallowed Compose masking errors (e.g. broken obfuscated internals) so regressions
    // fail this on-device test instead of silently under-masking (see SentryReplayDebug)
    System.setProperty("io.sentry.replay.compose.fail-fast", "true")
  }

  @Test
  fun captureComposeReplayFrameSnapshots() {
    val snapshotsDir =
      File(
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
          "sauce_labs_custom_screenshots",
        )
        .apply {
          deleteRecursively()
          mkdirs()
        }
    val frameReceived = CountDownLatch(1)
    val capturedScreens = CopyOnWriteArraySet<String>()
    val distinctColorsPerFrame = CopyOnWriteArraySet<Int>()

    val activityScenario = launchActivity<ComposeActivity>()
    activityScenario.moveToState(Lifecycle.State.RESUMED)

    initSentry {
      it.sessionReplay.sessionSampleRate = 1.0
      it.sessionReplay.frameObserver =
        SentryReplayOptions.ReplayFrameObserver { hint, frameTimestamp, screenName ->
          val bitmap =
            hint.getAs(TypeCheckHint.REPLAY_FRAME_BITMAP, Bitmap::class.java)
              ?: return@ReplayFrameObserver
          val name = screenName ?: "unknown"
          // EXPERIMENT: a failed or blank PixelCopy yields a single-color bitmap
          val colors = HashSet<Int>()
          for (x in 0 until bitmap.width step 8) {
            for (y in 0 until bitmap.height step 8) {
              colors.add(bitmap.getPixel(x, y))
            }
          }
          distinctColorsPerFrame.add(colors.size)
          Log.i(
            "PixelCopyExperiment",
            "frame screen=$name size=${bitmap.width}x${bitmap.height} distinctColors=${colors.size}",
          )
          if (capturedScreens.add(name)) {
            val file = File(snapshotsDir, "${name}_$frameTimestamp.png")
            file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
          }
          bitmap.recycle()
          frameReceived.countDown()
        }
    }

    assertTrue(frameReceived.await(10, TimeUnit.SECONDS), "Expected at least one replay frame")
    assertTrue(capturedScreens.isNotEmpty(), "Expected at least one screen captured")
    assertTrue(
      distinctColorsPerFrame.any { it > 1 },
      "Expected a non-blank frame, distinct colors per frame: $distinctColorsPerFrame",
    )

    val files = snapshotsDir.listFiles()?.filter { it.extension == "png" } ?: emptyList()
    assertTrue(files.isNotEmpty(), "Expected snapshot PNG files on disk")
    assertTrue(files.all { it.length() > 0 }, "Snapshot files should not be empty")

    activityScenario.moveToState(Lifecycle.State.DESTROYED)
  }
}
