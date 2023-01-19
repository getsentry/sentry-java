package io.sentry.android.core

import android.os.Build
import io.sentry.SentryInstantDate
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertTrue

class SentryAndroidDateProviderTest {

    private class Fixture {
        val buildInfoProvider = mock<BuildInfoProvider>()
    }

    private val fixture = Fixture()

    @Test
    fun `provides SentryInstantDate on newer Android API levels`() {
        whenever(fixture.buildInfoProvider.sdkInfoVersion).thenReturn(Build.VERSION_CODES.O)
        val date = SentryAndroidDateProvider(fixture.buildInfoProvider).now()
        assertTrue(date is SentryInstantDate)
    }
}
