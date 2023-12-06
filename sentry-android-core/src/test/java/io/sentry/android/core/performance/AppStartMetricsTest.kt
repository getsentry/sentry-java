package io.sentry.android.core.performance

import android.app.Application
import android.content.ContentProvider
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.android.core.SentryShadowProcess
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
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

        metrics.clear()

        assertTrue(metrics.appStartTimeSpan.hasNotStarted())
        assertTrue(metrics.sdkInitTimeSpan.hasNotStarted())
        assertTrue(metrics.applicationOnCreateTimeSpan.hasNotStarted())
        assertEquals(AppStartMetrics.AppStartType.UNKNOWN, metrics.appStartType)
        assertTrue(metrics.applicationOnCreateTimeSpan.hasNotStarted())

        assertTrue(metrics.activityLifecycleTimeSpans.isEmpty())
        assertTrue(metrics.contentProviderOnCreateTimeSpans.isEmpty())
    }

    @Test
    fun `if app start time span is started, appStartTimeSpanWithFallback returns it`() {
        val appStartTimeSpan = AppStartMetrics.getInstance().appStartTimeSpan
        appStartTimeSpan.start()

        val timeSpan = AppStartMetrics.getInstance().appStartTimeSpanWithFallback
        assertSame(appStartTimeSpan, timeSpan)
    }

    @Test
    fun `if app start time span is not started, appStartTimeSpanWithFallback returns the sdk init span instead`() {
        val appStartTimeSpan = AppStartMetrics.getInstance().appStartTimeSpan
        assertTrue(appStartTimeSpan.hasNotStarted())

        val timeSpan = AppStartMetrics.getInstance().appStartTimeSpanWithFallback
        val sdkInitSpan = AppStartMetrics.getInstance().sdkInitTimeSpan
        assertSame(sdkInitSpan, timeSpan)
    }
}
