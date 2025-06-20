package io.sentry.uitest.android.benchmark

import android.content.Context
import android.view.Choreographer
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.launchActivity
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import kotlin.test.BeforeTest

abstract class BaseBenchmarkTest {
  protected lateinit var runner: AndroidJUnitRunner
  protected lateinit var context: Context
  protected lateinit var choreographer: Choreographer

  @BeforeTest
  fun baseSetUp() {
    runner = InstrumentationRegistry.getInstrumentation() as AndroidJUnitRunner
    context = ApplicationProvider.getApplicationContext()
    context.cacheDir.deleteRecursively()
    // Must run on the main thread to get the main thread choreographer.
    runner.runOnMainSync { choreographer = Choreographer.getInstance() }

    // We need the refresh rate, but we can get it only from the activity, so we start and destroy
    // one
    val benchmarkScenario = launchActivity<BenchmarkActivity>()
    benchmarkScenario.moveToState(Lifecycle.State.DESTROYED)
  }
}
