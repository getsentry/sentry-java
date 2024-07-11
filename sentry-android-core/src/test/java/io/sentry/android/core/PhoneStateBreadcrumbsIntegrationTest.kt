package io.sentry.android.core

import android.content.Context
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import io.sentry.Breadcrumb
import io.sentry.IScopes
import io.sentry.ISentryExecutorService
import io.sentry.SentryLevel
import io.sentry.test.DeferredExecutorService
import io.sentry.test.ImmediateExecutorService
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
        val options = SentryAndroidOptions()

        fun getSut(executorService: ISentryExecutorService = ImmediateExecutorService()): PhoneStateBreadcrumbsIntegration {
            options.executorService = executorService
            whenever(context.getSystemService(eq(Context.TELEPHONY_SERVICE))).thenReturn(manager)

            return PhoneStateBreadcrumbsIntegration(context)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `When system events breadcrumb is enabled, it registers callback`() {
        val sut = fixture.getSut()
        val scopes = mock<IScopes>()
        sut.register(scopes, fixture.options)
        verify(fixture.manager).listen(any(), eq(PhoneStateListener.LISTEN_CALL_STATE))
        assertNotNull(sut.listener)
    }

    @Test
    fun `Phone state callback is registered in the executorService`() {
        val sut = fixture.getSut(mock())
        val scopes = mock<IScopes>()
        sut.register(scopes, fixture.options)

        assertNull(sut.listener)
    }

    @Test
    fun `When system events breadcrumb is disabled, it doesn't register callback`() {
        val sut = fixture.getSut()
        val scopes = mock<IScopes>()
        sut.register(
            scopes,
            fixture.options.apply {
                isEnableSystemEventBreadcrumbs = false
            }
        )
        verify(fixture.manager, never()).listen(any(), any())
        assertNull(sut.listener)
    }

    @Test
    fun `When ActivityBreadcrumbsIntegration is closed, it should unregister the callback`() {
        val sut = fixture.getSut()
        val scopes = mock<IScopes>()
        sut.register(scopes, fixture.options)
        sut.close()
        verify(fixture.manager).listen(any(), eq(PhoneStateListener.LISTEN_NONE))
        assertNull(sut.listener)
    }

    @Test
    fun `when scopes is closed right after start, integration is not registered`() {
        val deferredExecutorService = DeferredExecutorService()
        val sut = fixture.getSut(executorService = deferredExecutorService)
        sut.register(mock(), fixture.options)
        assertNull(sut.listener)
        sut.close()
        deferredExecutorService.runAll()
        assertNull(sut.listener)
    }

    @Test
    fun `When on call state received, added breadcrumb with type and category`() {
        val sut = fixture.getSut()
        val scopes = mock<IScopes>()
        sut.register(scopes, fixture.options)
        sut.listener!!.onCallStateChanged(TelephonyManager.CALL_STATE_RINGING, null)

        verify(scopes).addBreadcrumb(
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
        val scopes = mock<IScopes>()
        sut.register(scopes, fixture.options)
        sut.listener!!.onCallStateChanged(TelephonyManager.CALL_STATE_IDLE, null)
        verify(scopes, never()).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When on offhook state received, added breadcrumb with type and category`() {
        val sut = fixture.getSut()
        val scopes = mock<IScopes>()
        sut.register(scopes, fixture.options)
        sut.listener!!.onCallStateChanged(TelephonyManager.CALL_STATE_OFFHOOK, null)
        verify(scopes, never()).addBreadcrumb(any<Breadcrumb>())
    }
}
