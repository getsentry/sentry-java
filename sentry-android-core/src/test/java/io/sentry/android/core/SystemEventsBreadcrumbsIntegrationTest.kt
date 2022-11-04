package io.sentry.android.core

import android.content.Context
import android.content.Intent
import io.sentry.Breadcrumb
import io.sentry.IHub
import io.sentry.SentryLevel
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SystemEventsBreadcrumbsIntegrationTest {

    private class Fixture {
        val context = mock<Context>()
        var options = SentryAndroidOptions()
        val hub = mock<IHub>()

        fun getSut(enableSystemEventBreadcrumbs: Boolean = true): SystemEventsBreadcrumbsIntegration {
            options = SentryAndroidOptions().apply {
                isEnableSystemEventBreadcrumbs = enableSystemEventBreadcrumbs
            }
            return SystemEventsBreadcrumbsIntegration(context)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `When system events breadcrumb is enabled, it registers callback`() {
        val sut = fixture.getSut()

        sut.register(fixture.hub, fixture.options)

        verify(fixture.context).registerReceiver(any(), any())
        assertNotNull(sut.receiver)
    }

    @Test
    fun `When system events breadcrumb is disabled, it doesn't register callback`() {
        val sut = fixture.getSut(enableSystemEventBreadcrumbs = false)

        sut.register(fixture.hub, fixture.options)

        verify(fixture.context, never()).registerReceiver(any(), any())
        assertNull(sut.receiver)
    }

    @Test
    fun `When ActivityBreadcrumbsIntegration is closed, it should unregister the callback`() {
        val sut = fixture.getSut()

        sut.register(fixture.hub, fixture.options)
        sut.close()

        verify(fixture.context).unregisterReceiver(any())
        assertNull(sut.receiver)
    }

    @Test
    fun `When broadcast received, added breadcrumb with type and category`() {
        val sut = fixture.getSut()

        sut.register(fixture.hub, fixture.options)
        val intent = Intent().apply {
            action = Intent.ACTION_TIME_CHANGED
        }
        sut.receiver!!.onReceive(fixture.context, intent)

        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("device.event", it.category)
                assertEquals("system", it.type)
                assertEquals(SentryLevel.INFO, it.level)
                // cant assert data, its not a public API
            },
            anyOrNull()
        )
    }

    @Test
    fun `Do not crash if registerReceiver throws exception`() {
        val sut = fixture.getSut()
        whenever(fixture.context.registerReceiver(any(), any())).thenThrow(SecurityException())

        sut.register(fixture.hub, fixture.options)

        assertFalse(fixture.options.isEnableSystemEventBreadcrumbs)
    }
}
