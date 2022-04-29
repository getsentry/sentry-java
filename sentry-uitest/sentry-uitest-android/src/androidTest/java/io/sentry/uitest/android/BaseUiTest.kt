package io.sentry.uitest.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.idling.CountingIdlingResource
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.uitest.android.mockservers.MockRelay
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class BaseUiTest {

    /** Runner of the test. */
    protected lateinit var runner: AndroidJUnitRunner
    /** Application context for the current test. */
    protected lateinit var context: Context
    /** Mock dsn used to send envelopes to our mock [relay] server. */
    protected lateinit var mockDsn: String
        // The mockDsn cannot be changed. If a custom dsn needs to be used, it can be set in the options as usual
        private set
    /**
     * Idling resource that will be checked by the relay server (if [relayWaitForRequests] is true).
     * This should be increased to match any envelope that will be sent during the test,
     * so that they can later be checked.
     */
    protected val relayIdlingResource = CountingIdlingResource("relay-requests")
    /** Whether relay should wait for requests when asserting envelopes. Enable usage of [relayIdlingResource]. */
    private var relayWaitForRequests: Boolean = false
    /** Mock relay server that receives all envelopes sent during the test. */
    protected val relay = MockRelay(relayWaitForRequests, relayIdlingResource)

    @BeforeTest
    fun baseSetUp() {
        runner = InstrumentationRegistry.getInstrumentation() as AndroidJUnitRunner
        context = ApplicationProvider.getApplicationContext()
        context.cacheDir.deleteRecursively()
        relay.start()
        mockDsn = relay.createMockDsn()
    }

    @AfterTest
    fun baseFinish() {
        if (relayWaitForRequests) {
            IdlingRegistry.getInstance().unregister(relayIdlingResource)
        }
        relay.shutdown()
        Sentry.close()
    }

    /**
     * Initializes the Sentry sdk through [SentryAndroid.init] with a default dsn used to catch envelopes with [relay].
     * [relayWaitForRequests] sets whether [relay] should wait for all the envelopes sent when doing assertions.
     *  If true, [relayIdlingResource] should be increased to match any envelope that will be sent during the test.
     * Sentry options can be adjusted as usual through [optionsConfiguration].
     */
    protected fun initSentry(
        relayWaitForRequests: Boolean = false,
        optionsConfiguration: ((options: SentryOptions) -> Unit)? = null
    ) {
        this.relayWaitForRequests = relayWaitForRequests
        relay.waitForRequests = relayWaitForRequests
        if (relayWaitForRequests) {
            IdlingRegistry.getInstance().register(relayIdlingResource)
        }
        SentryAndroid.init(context) {
            it.dsn = mockDsn
            optionsConfiguration?.invoke(it)
        }
    }
}

/** Waits until the Sentry SDK is idle. */
fun waitUntilIdle() {
    // We rely on Espresso's idling resources.
    Espresso.onIdle()
}
