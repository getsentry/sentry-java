package io.sentry.android.core

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.IHub
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnrIntegrationTest {

    private val integration = AnrIntegration(mock())

    @BeforeTest
    fun `before each test`() {
        // watch dog is static and has shared state
        integration.close()
    }

    @Test
    fun `When ANR is enabled, ANR watch dog should be started`() {
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        integration.register(hub, options)
        assertNotNull(integration.anrWatchDog)
        assertTrue((integration.anrWatchDog as ANRWatchDog).isAlive)
    }

    @Test
    fun `When ANR is disabled, ANR should not be started`() {
        val options = SentryAndroidOptions()
        options.isAnrEnabled = false
        val hub = mock<IHub>()
        val integration = AnrIntegration(mock())
        integration.register(hub, options)
        assertNull(integration.anrWatchDog)
    }

    @Test
    fun `When ANR watch dog is triggered, it should capture exception`() {
        val hub = mock<IHub>()
        val integration = AnrIntegration(mock())
        integration.reportANR(hub, mock(), mock())
        verify(hub).captureException(any())
    }

    @Test
    fun `When ANR integration is closed, watch dog should stop`() {
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        val integration = AnrIntegration(mock())
        integration.register(hub, options)
        assertNotNull(integration.anrWatchDog)
        integration.close()
        assertNull(integration.anrWatchDog)
    }
}
