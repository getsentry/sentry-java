package io.sentry.android.uitests.end2end

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import io.sentry.android.uitests.end2end.mockservers.TestMockWebServers
import org.junit.runner.RunWith
import java.net.InetAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

@RunWith(AndroidJUnit4::class)
abstract class BaseUiTest {

    protected lateinit var runner: AndroidJUnitRunner
    protected lateinit var context: Context
    protected val servers = TestMockWebServers()

    protected val mainThreadId: Long
        get() {
            var id = -1L
            runner.runOnMainSync { id = android.os.Process.myTid().toLong() }
            check(id != -1L)
            return id
        }

    protected val applicationId: String
        get() = InstrumentationRegistry.getInstrumentation().context
            .packageName.removeSuffix(".test")

    protected val isAppDebuggable =
        (
            ApplicationProvider.getApplicationContext<Application>().applicationInfo.flags and
                ApplicationInfo.FLAG_DEBUGGABLE
            ) != 0

    /**
     * Waits until the Specto SDK is idle.
     */
    fun waitUntilIdle() {
        // We rely on Espresso's idling resources.
        Espresso.onIdle()
    }

    @BeforeTest
    fun baseSetUp() {
        runner = InstrumentationRegistry.getInstrumentation() as AndroidJUnitRunner
        context = ApplicationProvider.getApplicationContext()
        servers.relay.start()
    }

    @AfterTest
    fun baseFinish() {
        servers.relay.shutdown()
    }
}
