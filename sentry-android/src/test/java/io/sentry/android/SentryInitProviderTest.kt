package io.sentry.android

import android.content.Context
import android.content.pm.ProviderInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Sentry
import org.junit.runner.RunWith
import kotlin.test.BeforeTest
import kotlin.test.Test

@RunWith(AndroidJUnit4::class)
class SentryInitProviderTest {
    private var sentryInitProvider: SentryInitProvider = SentryInitProvider()

    private lateinit var context: Context

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `missing applicationId throws`() {
        val providerInfo = ProviderInfo()

        providerInfo.authority = "io.sentry.sentryInitProvider"
        sentryInitProvider.attachInfo(context, providerInfo)

        Sentry.init { o -> o.dsn = "test" }
    }
}
