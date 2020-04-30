package io.sentry.android.core

import android.content.Context
import android.content.Intent
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import io.sentry.core.Breadcrumb
import io.sentry.core.IHub
import io.sentry.core.SentryLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SystemEventsBreadcrumbsIntegrationTest {

    private class Fixture {
        val context = mock<Context>()

        fun getSut(): SystemEventsBreadcrumbsIntegration {
            return SystemEventsBreadcrumbsIntegration(context)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `When system events breadcrumb is enabled, it registers callback`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        verify(fixture.context).registerReceiver(any(), any())
        assertNotNull(sut.receiver)
    }

    @Test
    fun `When system events breadcrumb is disabled, it doesn't register callback`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions().apply {
            isEnableSystemEventBreadcrumbs = false
        }
        val hub = mock<IHub>()
        sut.register(hub, options)
        verify(fixture.context, never()).registerReceiver(any(), any())
        assertNull(sut.receiver)
    }

    @Test
    fun `When ActivityBreadcrumbsIntegration is closed, it should unregister the callback`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        sut.close()
        verify(fixture.context).unregisterReceiver(any())
        assertNull(sut.receiver)
    }

    @Test
    fun `When broadcast received, added breadcrumb with type and category`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        val intent = Intent().apply {
            action = Intent.ACTION_TIME_CHANGED
        }
        sut.receiver!!.onReceive(any(), intent)

        verify(hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("device.event", it.category)
            assertEquals("system", it.type)
            assertEquals(SentryLevel.INFO, it.level)
            // cant assert data, its not a public API
        })
    }
}
