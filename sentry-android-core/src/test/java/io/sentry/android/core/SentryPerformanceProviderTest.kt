package io.sentry.android.core

import android.content.pm.ProviderInfo
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.android.core.performance.AppStartMetrics
import io.sentry.android.core.performance.AppStartMetrics.AppStartType
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Implements(android.os.Process::class)
class SentryShadowProcess {

    companion object {

        private var startupTimeMillis: Long = 0

        fun setStartUptimeMillis(value: Long) {
            startupTimeMillis = value
        }

        @Suppress("unused")
        @Implementation
        @JvmStatic
        fun getStartUptimeMillis(): Long {
            return startupTimeMillis
        }
    }
}

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
        assertTrue(AppStartMetrics.getInstance().appStartTimeSpan.hasNotStarted())
        setupProvider()
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
    fun `provider sets keeps startup state even if multiple activities are launched`() {
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

    private fun setupProvider(): SentryPerformanceProvider {
        val providerInfo = ProviderInfo()

        val mockContext = ContextUtilsTestHelper.createMockContext(true)
        providerInfo.authority = AUTHORITY

        // calls onCreate
        val provider = SentryPerformanceProvider()
        provider.attachInfo(mockContext, providerInfo)
        return provider
    }

    private fun createView(): View {
        val view = View(ApplicationProvider.getApplicationContext())

        // Adding a listener forces ViewTreeObserver.mOnDrawListeners to be initialized and non-null.
        val dummyListener = ViewTreeObserver.OnDrawListener {}
        view.viewTreeObserver.addOnDrawListener(dummyListener)
        view.viewTreeObserver.removeOnDrawListener(dummyListener)

        return view
    }

    private fun runFirstDraw(view: View) {
        // Removes OnDrawListener in the next OnGlobalLayout after onDraw
        view.viewTreeObserver.dispatchOnDraw()
        view.viewTreeObserver.dispatchOnGlobalLayout()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    companion object {
        private const val AUTHORITY = "io.sentry.sample.SentryPerformanceProvider"
    }
}
