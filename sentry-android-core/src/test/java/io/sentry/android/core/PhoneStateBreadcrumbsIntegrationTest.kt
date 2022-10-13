package io.sentry.android.core

import android.content.Context
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import io.sentry.Breadcrumb
import io.sentry.IHub
import io.sentry.SentryLevel
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PhoneStateBreadcrumbsIntegrationTest {

    private class Fixture {
        val context = mock<Context>()
        val manager = mock<TelephonyManager>()

        fun getSut(): PhoneStateBreadcrumbsIntegration {
            whenever(context.getSystemService(eq(Context.TELEPHONY_SERVICE))).thenReturn(manager)

            return PhoneStateBreadcrumbsIntegration(context)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `When system events breadcrumb is enabled, it registers callback`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        verify(fixture.manager).listen(any(), eq(PhoneStateListener.LISTEN_CALL_STATE))
        assertNotNull(sut.listener)
    }

    @Test
    fun `When system events breadcrumb is disabled, it doesn't register callback`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions().apply {
            isEnableSystemEventBreadcrumbs = false
        }
        val hub = mock<IHub>()
        sut.register(hub, options)
        verify(fixture.manager, never()).listen(any(), any())
        assertNull(sut.listener)
    }

    @Test
    fun `When ActivityBreadcrumbsIntegration is closed, it should unregister the callback`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        sut.close()
        verify(fixture.manager).listen(any(), eq(PhoneStateListener.LISTEN_NONE))
        assertNull(sut.listener)
    }

    @Test
    fun `When on call state received, added breadcrumb with type and category`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        sut.listener!!.onCallStateChanged(TelephonyManager.CALL_STATE_RINGING, null)

        verify(hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("device.event", it.category)
                assertEquals("system", it.type)
                assertEquals(SentryLevel.INFO, it.level)
                // cant assert data, its not a public API
            }
        )
    }

    @Test
    fun `When on idle state received, added breadcrumb with type and category`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        sut.listener!!.onCallStateChanged(TelephonyManager.CALL_STATE_IDLE, null)
        verify(hub, never()).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When on offhook state received, added breadcrumb with type and category`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        sut.listener!!.onCallStateChanged(TelephonyManager.CALL_STATE_OFFHOOK, null)
        verify(hub, never()).addBreadcrumb(any<Breadcrumb>())
    }
}
