package io.sentry.android.core

import com.nhaarman.mockitokotlin2.mock
import io.sentry.core.IHub
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AppLifecycleIntegrationTest {

    private val integration = AppLifecycleIntegration()

    @Test
    fun `When AppLifecycleIntegration is added, lifecycle watcher should be started`() {
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        integration.register(hub, options)
        assertNotNull(integration.watcher)
    }

    @Test
    fun `When SessionTracking and AppLifecycle breadcrumbs are disabled, lifecycle watcher should not be started`() {
        val options = SentryAndroidOptions().apply {
            isEnableAppLifecycleBreadcrumbs = false
            isEnableSessionTracking = false
        }
        val hub = mock<IHub>()
        integration.register(hub, options)
        assertNull(integration.watcher)
    }

    @Test
    fun `When AppLifecycleIntegration is closed, lifecycle watcher should be closed`() {
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        integration.register(hub, options)
        assertNotNull(integration.watcher)
        integration.close()
        assertNull(integration.watcher)
    }
}
