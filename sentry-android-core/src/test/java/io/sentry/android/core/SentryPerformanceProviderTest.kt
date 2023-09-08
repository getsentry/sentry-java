package io.sentry.android.core

import android.app.Activity
import android.app.Application
import android.content.pm.ProviderInfo
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.SentryNanotimeDate
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Shadows
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SentryPerformanceProviderTest {

    @BeforeTest
    fun `set up`() {
        AppStartState.getInstance().resetInstance()
    }

    @Test
    fun `provider sets app start`() {
        val providerInfo = ProviderInfo()

        val mockContext = ContextUtilsTest.createMockContext()
        providerInfo.authority = AUTHORITY

        val providerAppStartMillis = 10L
        val providerAppStartTime = SentryNanotimeDate(Date(0), 0)
        SentryPerformanceProvider.setAppStartTime(providerAppStartMillis, providerAppStartTime)

        val provider = SentryPerformanceProvider()
        provider.attachInfo(mockContext, providerInfo)

        // done by ActivityLifecycleIntegration so forcing it here
        val lifecycleAppEndMillis = 20L
        AppStartState.getInstance().setAppStartEnd(lifecycleAppEndMillis)
        AppStartState.getInstance().setColdStart(true)

        assertEquals(10L, AppStartState.getInstance().appStartInterval)
    }

    @Test
    fun `provider sets first activity as cold start`() {
        val providerInfo = ProviderInfo()

        val mockContext = ContextUtilsTest.createMockContext()
        providerInfo.authority = AUTHORITY

        val provider = SentryPerformanceProvider()
        provider.attachInfo(mockContext, providerInfo)

        provider.onActivityCreated(mock(), null)

        assertTrue(AppStartState.getInstance().isColdStart!!)
    }

    @Test
    fun `provider sets first activity as warm start`() {
        val providerInfo = ProviderInfo()

        val mockContext = ContextUtilsTest.createMockContext()
        providerInfo.authority = AUTHORITY

        val provider = SentryPerformanceProvider()
        provider.attachInfo(mockContext, providerInfo)

        provider.onActivityCreated(mock(), Bundle())

        assertFalse(AppStartState.getInstance().isColdStart!!)
    }

    @Test
    fun `provider sets app start end on first activity resume, and unregisters afterwards`() {
        val providerInfo = ProviderInfo()

        val mockContext = ContextUtilsTest.createMockContext(true)
        providerInfo.authority = AUTHORITY

        val provider = SentryPerformanceProvider(
            mock {
                whenever(mock.sdkInfoVersion).thenReturn(Build.VERSION_CODES.Q)
            },
            MainLooperHandler()
        )
        provider.attachInfo(mockContext, providerInfo)

        val view = createView()
        val activity = mock<Activity>()
        whenever(activity.findViewById<View>(any())).thenReturn(view)
        provider.onActivityCreated(activity, Bundle())
        provider.onActivityResumed(activity)
        Thread.sleep(1)
        runFirstDraw(view)

        assertNotNull(AppStartState.getInstance().appStartInterval)
        assertNotNull(AppStartState.getInstance().appStartEndTime)

        verify((mockContext.applicationContext as Application))
            .unregisterActivityLifecycleCallbacks(any())
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
