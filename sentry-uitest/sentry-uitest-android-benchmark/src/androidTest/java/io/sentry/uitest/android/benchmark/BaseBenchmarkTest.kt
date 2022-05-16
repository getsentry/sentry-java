package io.sentry.uitest.android.benchmark

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import kotlin.test.BeforeTest

abstract class BaseBenchmarkTest {

    protected lateinit var runner: AndroidJUnitRunner
    protected lateinit var context: Context

    @BeforeTest
    fun baseSetUp() {
        runner = InstrumentationRegistry.getInstrumentation() as AndroidJUnitRunner
        context = ApplicationProvider.getApplicationContext()
        context.cacheDir.deleteRecursively()
    }
}
