package io.sentry.uitest.android

import android.graphics.Bitmap
import android.os.Environment
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import io.sentry.Sentry
import io.sentry.android.replay.ReplayIntegration
import io.sentry.android.replay.ReplaySnapshotObserver
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assume.assumeThat
import org.junit.Before

class ReplaySnapshotTest : BaseUiTest() {

  @Before
  fun setup() {
    // GH Actions emulators don't support capturing screenshots for replay
    @Suppress("KotlinConstantConditions")
    assumeThat(BuildConfig.ENVIRONMENT != "github", `is`(true))
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

    val activityScenario = launchActivity<ComposeActivity>()
    activityScenario.moveToState(Lifecycle.State.RESUMED)

    initSentry { it.sessionReplay.sessionSampleRate = 1.0 }

    val integration = Sentry.getCurrentScopes().options.replayController as? ReplayIntegration
    integration?.snapshotObserver = ReplaySnapshotObserver { bitmap, frameTimestamp, screenName ->
      val name = screenName ?: "unknown"
      if (capturedScreens.add(name)) {
        val file = File(snapshotsDir, "${name}_$frameTimestamp.png")
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
      }
      frameReceived.countDown()
    }

    assertTrue(frameReceived.await(10, TimeUnit.SECONDS), "Expected at least one replay frame")
    assertTrue(capturedScreens.isNotEmpty(), "Expected at least one screen captured")

    val files = snapshotsDir.listFiles()?.filter { it.extension == "png" } ?: emptyList()
    assertTrue(files.isNotEmpty(), "Expected snapshot PNG files on disk")
    assertTrue(files.all { it.length() > 0 }, "Snapshot files should not be empty")

    activityScenario.moveToState(Lifecycle.State.DESTROYED)
  }
}
