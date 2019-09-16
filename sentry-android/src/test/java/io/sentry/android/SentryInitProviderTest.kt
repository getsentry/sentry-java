package io.sentry.android

import android.content.Context
import android.content.pm.ProviderInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Sentry
import org.junit.runner.RunWith
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SentryInitProviderTest {
    private var sentryInitProvider: SentryInitProvider = SentryInitProvider()

    private lateinit var context: Context

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
        Sentry.close()
    }

    @Test
    fun `when missing applicationId, SentryInitProvider throws`() {
        val providerInfo = ProviderInfo()

        providerInfo.authority = "io.sentry.android.SentryInitProvider"
        sentryInitProvider.attachInfo(context, providerInfo)
    }

    @Test
    fun `when applicationId defined, SDK initializes`() {
        val providerInfo = ProviderInfo()

        assertFalse(Sentry.isEnabled())
        providerInfo.authority = "io.sentry.android.SentryInitProvider"
        sentryInitProvider.attachInfo(context, providerInfo)

        assertTrue(Sentry.isEnabled())
    }
}
