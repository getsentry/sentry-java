package io.sentry.uitest.android.benchmark

import android.content.Context
import android.view.Choreographer
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import kotlin.test.BeforeTest

abstract class BaseBenchmarkTest {

    protected lateinit var runner: AndroidJUnitRunner
    protected lateinit var context: Context
    protected lateinit var choreographer: Choreographer
    protected var refreshRate: Float = 60F

    @BeforeTest
    fun baseSetUp() {
        runner = InstrumentationRegistry.getInstrumentation() as AndroidJUnitRunner
        context = ApplicationProvider.getApplicationContext()
        context.cacheDir.deleteRecursively()
        refreshRate = context.display?.refreshRate ?: 60F
        // Must run on the main thread to get the main thread choreographer.
        runner.runOnMainSync {
            choreographer = Choreographer.getInstance()
        }
    }
}
