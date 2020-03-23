package io.sentry.android.core

import com.nhaarman.mockitokotlin2.mock
import io.sentry.core.IHub
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SessionTrackingIntegrationTest {

    private val integration = SessionTrackingIntegration()

    @Test
    fun `When SessionTracking is enabled, lifecycle watcher should be started`() {
        val options = SentryAndroidOptions()
        options.isEnableSessionTracking = true
        val hub = mock<IHub>()
        integration.register(hub, options)
        assertNotNull(integration.watcher)
    }

    @Test
    fun `When SessionTracking is disabled, lifecycle watcher should not be started`() {
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        integration.register(hub, options)
        assertNull(integration.watcher)
    }

    @Test
    fun `When SessionTracking integration is closed, lifecycle watcher should be closed`() {
        val options = SentryAndroidOptions()
        options.isEnableSessionTracking = true
        val hub = mock<IHub>()
        integration.register(hub, options)
        assertNotNull(integration.watcher)
        integration.close()
        assertNull(integration.watcher)
    }
}
