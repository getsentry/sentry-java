package io.sentry.android.core.performance

import android.app.Application
import android.content.ContentProvider
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.android.core.SentryShadowProcess
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
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
        metrics.setAppStartProfiler(mock())
        metrics.appStartSamplingDecision = mock()

        metrics.clear()

        assertTrue(metrics.appStartTimeSpan.hasNotStarted())
        assertTrue(metrics.sdkInitTimeSpan.hasNotStarted())
        assertTrue(metrics.applicationOnCreateTimeSpan.hasNotStarted())
        assertEquals(AppStartMetrics.AppStartType.UNKNOWN, metrics.appStartType)

        assertTrue(metrics.activityLifecycleTimeSpans.isEmpty())
        assertTrue(metrics.contentProviderOnCreateTimeSpans.isEmpty())
        assertNull(metrics.appStartProfiler)
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
}
