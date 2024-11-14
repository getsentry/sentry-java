package io.sentry.android.core.performance

import android.app.Application
import android.content.ContentProvider
import android.os.Build
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.IContinuousProfiler
import io.sentry.ITransactionProfiler
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.android.core.SentryShadowProcess
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [Build.VERSION_CODES.N],
    shadows = [SentryShadowProcess::class]
)
class AppStartMetricsTest {

    @Before
    fun setup() {
        AppStartMetrics.getInstance().clear()
        SentryShadowProcess.setStartUptimeMillis(42)
        AppStartMetrics.getInstance().isAppLaunchedInForeground = true
    }

    @Test
    fun `getInstance returns a singleton`() {
        assertSame(AppStartMetrics.getInstance(), AppStartMetrics.getInstance())
    }

    @Test
    fun `metrics are properly cleared`() {
        val metrics = AppStartMetrics.getInstance()
        metrics.appStartTimeSpan.start()
        metrics.sdkInitTimeSpan.start()
        metrics.appStartType = AppStartMetrics.AppStartType.WARM
        metrics.applicationOnCreateTimeSpan.start()
        metrics.addActivityLifecycleTimeSpans(ActivityLifecycleTimeSpan())
        AppStartMetrics.onApplicationCreate(mock<Application>())
        AppStartMetrics.onContentProviderCreate(mock<ContentProvider>())
        metrics.appStartProfiler = mock()
        metrics.appStartContinuousProfiler = mock()
        metrics.appStartSamplingDecision = mock()

        metrics.clear()

        assertTrue(metrics.appStartTimeSpan.hasNotStarted())
        assertTrue(metrics.sdkInitTimeSpan.hasNotStarted())
        assertTrue(metrics.applicationOnCreateTimeSpan.hasNotStarted())
        assertEquals(AppStartMetrics.AppStartType.UNKNOWN, metrics.appStartType)

        assertTrue(metrics.activityLifecycleTimeSpans.isEmpty())
        assertTrue(metrics.contentProviderOnCreateTimeSpans.isEmpty())
        assertNull(metrics.appStartProfiler)
        assertNull(metrics.appStartContinuousProfiler)
        assertNull(metrics.appStartSamplingDecision)
    }

    @Test
    fun `if perf-2 is enabled and app start time span is started, appStartTimeSpanWithFallback returns it`() {
        val appStartTimeSpan = AppStartMetrics.getInstance().appStartTimeSpan
        appStartTimeSpan.start()

        val options = SentryAndroidOptions().apply {
            isEnablePerformanceV2 = true
        }

        val timeSpan = AppStartMetrics.getInstance().getAppStartTimeSpanWithFallback(options)
        assertSame(appStartTimeSpan, timeSpan)
    }

    @Test
    fun `if perf-2 is disabled but app start time span has started, appStartTimeSpanWithFallback returns the sdk init span instead`() {
        val appStartTimeSpan = AppStartMetrics.getInstance().appStartTimeSpan
        appStartTimeSpan.start()

        val options = SentryAndroidOptions().apply {
            isEnablePerformanceV2 = false
        }

        val timeSpan = AppStartMetrics.getInstance().getAppStartTimeSpanWithFallback(options)
        val sdkInitSpan = AppStartMetrics.getInstance().sdkInitTimeSpan
        assertSame(sdkInitSpan, timeSpan)
    }

    @Test
    fun `if perf-2 is enabled but app start time span has not started, appStartTimeSpanWithFallback returns the sdk init span instead`() {
        val appStartTimeSpan = AppStartMetrics.getInstance().appStartTimeSpan
        assertTrue(appStartTimeSpan.hasNotStarted())

        val options = SentryAndroidOptions().apply {
            isEnablePerformanceV2 = true
        }

        val timeSpan = AppStartMetrics.getInstance().getAppStartTimeSpanWithFallback(options)
        val sdkInitSpan = AppStartMetrics.getInstance().sdkInitTimeSpan
        assertSame(sdkInitSpan, timeSpan)
    }

    @Test
    fun `class load time is set`() {
        assertNotEquals(0, AppStartMetrics.getInstance().classLoadedUptimeMs)
    }

    @Test
    fun `if app is launched in background, appStartTimeSpanWithFallback returns an empty span`() {
        AppStartMetrics.getInstance().isAppLaunchedInForeground = false
        val appStartTimeSpan = AppStartMetrics.getInstance().appStartTimeSpan
        appStartTimeSpan.start()
        assertTrue(appStartTimeSpan.hasStarted())
        AppStartMetrics.getInstance().onActivityCreated(mock(), mock())
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val options = SentryAndroidOptions().apply {
            isEnablePerformanceV2 = false
        }

        val timeSpan = AppStartMetrics.getInstance().getAppStartTimeSpanWithFallback(options)
        assertFalse(timeSpan.hasStarted())
    }

    @Test
    fun `if app is launched in background with perfV2, appStartTimeSpanWithFallback returns an empty span`() {
        val appStartTimeSpan = AppStartMetrics.getInstance().appStartTimeSpan
        appStartTimeSpan.start()
        assertTrue(appStartTimeSpan.hasStarted())
        AppStartMetrics.getInstance().isAppLaunchedInForeground = false
        AppStartMetrics.getInstance().onActivityCreated(mock(), mock())

        val options = SentryAndroidOptions().apply {
            isEnablePerformanceV2 = true
        }

        val timeSpan = AppStartMetrics.getInstance().getAppStartTimeSpanWithFallback(options)
        assertFalse(timeSpan.hasStarted())
    }

    @Test
    fun `if app start span is at most 1 minute, appStartTimeSpanWithFallback returns the app start span`() {
        val appStartTimeSpan = AppStartMetrics.getInstance().appStartTimeSpan
        appStartTimeSpan.start()
        appStartTimeSpan.stop()
        appStartTimeSpan.setStartedAt(1)
        appStartTimeSpan.setStoppedAt(TimeUnit.MINUTES.toMillis(1) + 1)
        assertTrue(appStartTimeSpan.hasStarted())
        AppStartMetrics.getInstance().onActivityCreated(mock(), mock())

        val options = SentryAndroidOptions().apply {
            isEnablePerformanceV2 = true
        }

        val timeSpan = AppStartMetrics.getInstance().getAppStartTimeSpanWithFallback(options)
        assertTrue(timeSpan.hasStarted())
        assertSame(appStartTimeSpan, timeSpan)
    }

    @Test
    fun `if activity is never started, returns an empty span`() {
        AppStartMetrics.getInstance().registerApplicationForegroundCheck(mock())
        val appStartTimeSpan = AppStartMetrics.getInstance().appStartTimeSpan
        appStartTimeSpan.setStartedAt(1)
        assertTrue(appStartTimeSpan.hasStarted())
        // Job on main thread checks if activity was launched
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val timeSpan = AppStartMetrics.getInstance().getAppStartTimeSpanWithFallback(SentryAndroidOptions())
        assertFalse(timeSpan.hasStarted())
    }

    @Test
    fun `if activity is never started, stops app start profiler if running`() {
        val profiler = mock<ITransactionProfiler>()
        whenever(profiler.isRunning).thenReturn(true)
        AppStartMetrics.getInstance().appStartProfiler = profiler

        AppStartMetrics.getInstance().registerApplicationForegroundCheck(mock())
        // Job on main thread checks if activity was launched
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        verify(profiler).close()
    }

    @Test
    fun `if activity is never started, stops app start continuous profiler if running`() {
        val profiler = mock<IContinuousProfiler>()
        whenever(profiler.isRunning).thenReturn(true)
        AppStartMetrics.getInstance().appStartContinuousProfiler = profiler

        AppStartMetrics.getInstance().registerApplicationForegroundCheck(mock())
        // Job on main thread checks if activity was launched
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        verify(profiler).close()
    }

    @Test
    fun `if activity is started, does not stop app start profiler if running`() {
        val profiler = mock<ITransactionProfiler>()
        whenever(profiler.isRunning).thenReturn(true)
        AppStartMetrics.getInstance().appStartProfiler = profiler
        AppStartMetrics.getInstance().onActivityCreated(mock(), mock())

        AppStartMetrics.getInstance().registerApplicationForegroundCheck(mock())
        // Job on main thread checks if activity was launched
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        verify(profiler, never()).close()
    }

    @Test
    fun `if activity is started, does not stop app start continuous profiler if running`() {
        val profiler = mock<IContinuousProfiler>()
        whenever(profiler.isRunning).thenReturn(true)
        AppStartMetrics.getInstance().appStartContinuousProfiler = profiler
        AppStartMetrics.getInstance().onActivityCreated(mock(), mock())

        AppStartMetrics.getInstance().registerApplicationForegroundCheck(mock())
        // Job on main thread checks if activity was launched
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        verify(profiler, never()).close()
    }

    @Test
    fun `if app start span is longer than 1 minute, appStartTimeSpanWithFallback returns an empty span`() {
        val appStartTimeSpan = AppStartMetrics.getInstance().appStartTimeSpan
        appStartTimeSpan.start()
        appStartTimeSpan.stop()
        appStartTimeSpan.setStartedAt(1)
        appStartTimeSpan.setStoppedAt(TimeUnit.MINUTES.toMillis(1) + 2)
        assertTrue(appStartTimeSpan.hasStarted())
        AppStartMetrics.getInstance().onActivityCreated(mock(), mock())

        val options = SentryAndroidOptions().apply {
            isEnablePerformanceV2 = true
        }

        val timeSpan = AppStartMetrics.getInstance().getAppStartTimeSpanWithFallback(options)
        assertFalse(timeSpan.hasStarted())
    }

    @Test
    fun `when multiple registerApplicationForegroundCheck, only one callback is registered to application`() {
        val application = mock<Application>()
        AppStartMetrics.getInstance().registerApplicationForegroundCheck(application)
        AppStartMetrics.getInstance().registerApplicationForegroundCheck(application)
        verify(application, times(1)).registerActivityLifecycleCallbacks(eq(AppStartMetrics.getInstance()))
    }

    @Test
    fun `when registerApplicationForegroundCheck, a callback is registered to application`() {
        val application = mock<Application>()
        AppStartMetrics.getInstance().registerApplicationForegroundCheck(application)
        verify(application).registerActivityLifecycleCallbacks(eq(AppStartMetrics.getInstance()))
    }

    @Test
    fun `when registerApplicationForegroundCheck, a job is posted on main thread to unregistered the callback`() {
        val application = mock<Application>()
        AppStartMetrics.getInstance().registerApplicationForegroundCheck(application)
        verify(application).registerActivityLifecycleCallbacks(eq(AppStartMetrics.getInstance()))
        verify(application, never()).unregisterActivityLifecycleCallbacks(eq(AppStartMetrics.getInstance()))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        verify(application).unregisterActivityLifecycleCallbacks(eq(AppStartMetrics.getInstance()))
    }

    @Test
    fun `registerApplicationForegroundCheck set foreground state to false if no activity is running`() {
        val application = mock<Application>()
        AppStartMetrics.getInstance().isAppLaunchedInForeground = true
        AppStartMetrics.getInstance().registerApplicationForegroundCheck(application)
        assertTrue(AppStartMetrics.getInstance().isAppLaunchedInForeground)
        // Main thread performs the check and sets the flag to false if no activity was created
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertFalse(AppStartMetrics.getInstance().isAppLaunchedInForeground)
    }

    @Test
    fun `registerApplicationForegroundCheck keeps foreground state to true if an activity is running`() {
        val application = mock<Application>()
        AppStartMetrics.getInstance().isAppLaunchedInForeground = true
        AppStartMetrics.getInstance().registerApplicationForegroundCheck(application)
        assertTrue(AppStartMetrics.getInstance().isAppLaunchedInForeground)
        // An activity was created
        AppStartMetrics.getInstance().onActivityCreated(mock(), null)
        // Main thread performs the check and keeps the flag to true
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(AppStartMetrics.getInstance().isAppLaunchedInForeground)
    }
}
