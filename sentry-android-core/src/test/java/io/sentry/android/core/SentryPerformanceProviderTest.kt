package io.sentry.android.core

import android.content.pm.ProviderInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith

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
        val providerAppStartTime = Date(0)
        SentryPerformanceProvider.setAppStartTime(providerAppStartMillis, providerAppStartTime)

        val provider = SentryPerformanceProvider()
        provider.attachInfo(mockContext, providerInfo)

        // done by ActivityLifecycleIntegration so forcing it here
        val lifecycleAppEndMillis = 20L
        AppStartState.getInstance().setAppStartEnd(lifecycleAppEndMillis)

        assertEquals(10L, AppStartState.getInstance().appStartInterval)
    }

    companion object {
        private const val AUTHORITY = "io.sentry.sample.SentryPerformanceProvider"
    }
}
