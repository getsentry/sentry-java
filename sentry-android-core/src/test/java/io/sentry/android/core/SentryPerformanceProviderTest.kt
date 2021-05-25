package io.sentry.android.core

import android.content.pm.ProviderInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
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

        val provider = SentryPerformanceProvider()
        provider.attachInfo(mockContext, providerInfo)

        // done by ActivityLifecycleIntegration so forcing it here
        AppStartState.getInstance().setAppStartEnd()

        assertNotNull(AppStartState.getInstance().appStartInterval)
    }

    companion object {
        private const val AUTHORITY = "io.sentry.sample.SentryPerformanceProvider"
    }
}
