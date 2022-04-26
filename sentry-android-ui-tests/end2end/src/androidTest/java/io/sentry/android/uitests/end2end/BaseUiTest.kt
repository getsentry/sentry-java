package io.sentry.android.uitests.end2end

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.idling.CountingIdlingResource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.android.uitests.end2end.mockservers.MockRelay
import org.junit.runner.RunWith
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

@RunWith(AndroidJUnit4::class)
abstract class BaseUiTest {

    protected val relayIdlingResource = CountingIdlingResource("relay-requests")
    protected lateinit var runner: AndroidJUnitRunner
    protected lateinit var context: Context
    protected lateinit var dsn: String
    protected val relay = MockRelay(false, relayIdlingResource)

    init {
    }

    @BeforeTest
    fun baseSetUp() {
        runner = InstrumentationRegistry.getInstrumentation() as AndroidJUnitRunner
        context = ApplicationProvider.getApplicationContext()
        context.cacheDir.deleteRecursively()
        IdlingRegistry.getInstance().register(relayIdlingResource)
        relay.start()
        dsn = "http://key@${relay.hostName}:${relay.port}/${relay.dsnProject}"
    }

    @AfterTest
    fun baseFinish() {
        IdlingRegistry.getInstance().unregister(relayIdlingResource)
        relay.shutdown()
        Sentry.close()
    }

    protected fun initSentry(
        relayCheckIdlingResources: Boolean = true,
        optionsConfiguration: ((options: SentryOptions) -> Unit)? = null
    ) {
        relay.checkIdlingResources = relayCheckIdlingResources
        SentryAndroid.init(context) {
            it.dsn = dsn
            optionsConfiguration?.invoke(it)
        }
    }
}

/**
 * Waits until the Specto SDK is idle.
 */
fun waitUntilIdle() {
    // We rely on Espresso's idling resources.
    Espresso.onIdle()
}
