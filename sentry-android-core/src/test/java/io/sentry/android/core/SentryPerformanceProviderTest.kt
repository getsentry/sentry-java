package io.sentry.android.core

import android.app.Application
import android.content.Context
import android.content.pm.ProviderInfo
import android.os.Build
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.android.core.performance.AppStartMetrics
import io.sentry.android.core.performance.AppStartMetrics.AppStartType
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [Build.VERSION_CODES.N],
    shadows = [SentryShadowProcess::class]
)
class SentryPerformanceProviderTest {

    @BeforeTest
    fun `set up`() {
        AppStartMetrics.getInstance().clear()
        SentryShadowProcess.setStartUptimeMillis(1234)
    }

    @Test
    fun `provider starts appStartTimeSpan`() {
        assertTrue(AppStartMetrics.getInstance().sdkInitTimeSpan.hasNotStarted())
        assertTrue(AppStartMetrics.getInstance().appStartTimeSpan.hasNotStarted())
        setupProvider()
        assertTrue(AppStartMetrics.getInstance().sdkInitTimeSpan.hasStarted())
        assertTrue(AppStartMetrics.getInstance().appStartTimeSpan.hasStarted())
    }

    @Test
    fun `provider sets cold start based on first activity`() {
        val provider = setupProvider()

        // up until this point app start is not known
        assertEquals(AppStartType.UNKNOWN, AppStartMetrics.getInstance().appStartType)

        // when there's no saved state
        provider.activityCallback!!.onActivityCreated(mock(), null)
        // then app start should be cold
        assertEquals(AppStartType.COLD, AppStartMetrics.getInstance().appStartType)
    }

    @Test
    fun `provider sets warm start based on first activity`() {
        val provider = setupProvider()

        // up until this point app start is not known
        assertEquals(AppStartType.UNKNOWN, AppStartMetrics.getInstance().appStartType)

        // when there's a saved state
        provider.activityCallback!!.onActivityCreated(mock(), Bundle())

        // then app start should be warm
        assertEquals(AppStartType.WARM, AppStartMetrics.getInstance().appStartType)
    }

    @Test
    fun `provider keeps startup state even if multiple activities are launched`() {
        val provider = setupProvider()

        // when there's a saved state
        provider.activityCallback!!.onActivityCreated(mock(), Bundle())

        // then app start should be warm
        assertEquals(AppStartType.WARM, AppStartMetrics.getInstance().appStartType)

        // when another activity is launched cold
        provider.activityCallback!!.onActivityCreated(mock(), null)

        // then app start should remain warm
        assertEquals(AppStartType.WARM, AppStartMetrics.getInstance().appStartType)
    }

    @Test
    fun `provider sets both appstart and sdk init start + end times`() {
        val provider = setupProvider()
        provider.onAppStartDone()

        val metrics = AppStartMetrics.getInstance()
        assertTrue(metrics.appStartTimeSpan.hasStarted())
        assertTrue(metrics.appStartTimeSpan.hasStopped())

        assertTrue(metrics.sdkInitTimeSpan.hasStarted())
        assertTrue(metrics.sdkInitTimeSpan.hasStopped())
    }

    @Test
    fun `provider properly registers and unregisters ActivityLifecycleCallbacks`() {
        val context = mock<Application>()
        val provider = setupProvider(context)

        verify(context).registerActivityLifecycleCallbacks(any())
        provider.onAppStartDone()
        verify(context).unregisterActivityLifecycleCallbacks(any())
    }

    private fun setupProvider(context: Context = mock<Application>()): SentryPerformanceProvider {
        val providerInfo = ProviderInfo()
        providerInfo.authority = AUTHORITY

        // calls onCreate under the hood
        val provider = SentryPerformanceProvider()
        provider.attachInfo(context, providerInfo)
        return provider
    }

    companion object {
        private const val AUTHORITY = "io.sentry.sample.SentryPerformanceProvider"
    }
}
