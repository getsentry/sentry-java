package io.sentry.uitest.android

import android.graphics.Bitmap
import android.os.Environment
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import io.sentry.SentryReplayOptions
import io.sentry.TypeCheckHint
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

class ReplaySnapshotTest : BaseUiTest() {

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
    val capturedScreens = CopyOnWriteArrayList<String>()

    val activityScenario = launchActivity<ComposeActivity>()
    activityScenario.moveToState(Lifecycle.State.RESUMED)

    initSentry {
      it.sessionReplay.sessionSampleRate = 1.0
      it.sessionReplay.setBeforeStoreFrame(
        SentryReplayOptions.BeforeStoreFrameCallback { hint, frameTimestamp, screenName ->
          val frameBitmap =
            hint.getAs(TypeCheckHint.REPLAY_FRAME_BITMAP, Bitmap::class.java)
              ?: return@BeforeStoreFrameCallback
          val name = screenName ?: "unknown"
          if (!capturedScreens.contains(name)) {
            val file = File(snapshotsDir, "${name}_$frameTimestamp.png")
            file.outputStream().use { out ->
              frameBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            capturedScreens.add(name)
          }
          frameReceived.countDown()
        }
      )
    }

    assertTrue(frameReceived.await(10, TimeUnit.SECONDS), "Expected at least one replay frame")
    assertTrue(capturedScreens.isNotEmpty(), "Expected at least one screen captured")

    val files = snapshotsDir.listFiles()?.filter { it.extension == "png" } ?: emptyList()
    assertTrue(files.isNotEmpty(), "Expected snapshot PNG files on disk")
    assertTrue(files.all { it.length() > 0 }, "Snapshot files should not be empty")

    activityScenario.moveToState(Lifecycle.State.DESTROYED)
  }
}
