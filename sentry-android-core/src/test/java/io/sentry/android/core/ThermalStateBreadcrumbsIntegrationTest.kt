package io.sentry.android.core

import android.content.Context
import android.os.Build
import android.os.PowerManager
import io.sentry.Breadcrumb
import io.sentry.IHub
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals

class ThermalStateBreadcrumbsIntegrationTest {

    private class Fixture {

        val context = mock<Context>()
        val powerManager = mock<PowerManager>()
        var options = SentryAndroidOptions()
        val buildInfo = mock<BuildInfoProvider>()

        val hub = mock<IHub>()

        fun getSut(apiLevel: Int = Build.VERSION_CODES.Q, enabled: Boolean = true): ThermalStateBreadcrumbsIntegration {
            whenever(buildInfo.sdkInfoVersion).thenReturn(apiLevel)
            whenever(context.getSystemService(any())).thenReturn(powerManager)

            options.isEnableThermalStateBreadcrumbs = enabled
            return ThermalStateBreadcrumbsIntegration(context, buildInfo)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `When integration is registered, it registers thermal callback`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)
        verify(fixture.powerManager).addThermalStatusListener(any())
    }

    @Test
    fun `When integration is registered, but option is disabled it does not register thermal callback`() {
        val sut = fixture.getSut(enabled = false)
        sut.register(fixture.hub, fixture.options)
        verify(fixture.powerManager, never()).addThermalStatusListener(any())
        verify(fixture.hub, never()).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `When integration is registered pre-Q, it does not register thermal callback`() {
        val sut = fixture.getSut(Build.VERSION_CODES.P)
        sut.register(fixture.hub, fixture.options)
        verify(fixture.powerManager, never()).addThermalStatusListener(any())
        sut.close()
        verify(fixture.powerManager, never()).removeThermalStatusListener(any())
    }

    @Test
    fun `When integration is closed, it unregisters thermal callback`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        sut.close()
        verify(fixture.powerManager).removeThermalStatusListener(any())
    }

    @Test
    fun `Creates proper breadcrumbs`() {
        var listener: PowerManager.OnThermalStatusChangedListener = mock()
        whenever(fixture.powerManager.addThermalStatusListener(any())).then {
            listener = it.arguments[0] as PowerManager.OnThermalStatusChangedListener
            return@then Unit
        }
        whenever(fixture.powerManager.currentThermalStatus).thenReturn(PowerManager.THERMAL_STATUS_EMERGENCY)

        val breadcrumbs = mutableListOf<Breadcrumb>()
        whenever(fixture.hub.addBreadcrumb(any<Breadcrumb>())).then {
            breadcrumbs.add(it.arguments[0] as Breadcrumb)
        }

        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)
        verify(fixture.powerManager).addThermalStatusListener(any())

        // Test all possible states and ensure no duplicates are reported
        listener.onThermalStatusChanged(PowerManager.THERMAL_STATUS_NONE)
        listener.onThermalStatusChanged(PowerManager.THERMAL_STATUS_NONE)
        listener.onThermalStatusChanged(PowerManager.THERMAL_STATUS_LIGHT)
        listener.onThermalStatusChanged(PowerManager.THERMAL_STATUS_MODERATE)
        listener.onThermalStatusChanged(PowerManager.THERMAL_STATUS_CRITICAL)
        listener.onThermalStatusChanged(PowerManager.THERMAL_STATUS_SEVERE)
        listener.onThermalStatusChanged(PowerManager.THERMAL_STATUS_SEVERE)
        listener.onThermalStatusChanged(PowerManager.THERMAL_STATUS_EMERGENCY)
        listener.onThermalStatusChanged(PowerManager.THERMAL_STATUS_SHUTDOWN)
        listener.onThermalStatusChanged(-1)

        val expected = listOf(
            "emergency",
            "none",
            "light",
            "moderate",
            "critical",
            "severe",
            "emergency",
            "shutdown",
            "unknown"
        )

        breadcrumbs.forEachIndexed { index, breadcrumb ->
            assertEquals("Thermal status changed: ${expected[index]}", breadcrumb.message)
        }
    }
}
