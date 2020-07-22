package io.sentry.android.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SentryAndroidOptionsTest {

    @Test
    fun `init should set clientName`() {
        val sentryOptions = SentryAndroidOptions()

        val clientName = "${BuildConfig.SENTRY_CLIENT_NAME}/${BuildConfig.VERSION_NAME}"

        assertEquals(clientName, sentryOptions.sentryClientName)
    }

    @Test
    fun `init should set SdkVersion`() {
        val sentryOptions = SentryAndroidOptions()
        assertNotNull(sentryOptions.sdkVersion)
        val sdkVersion = sentryOptions.sdkVersion!!

        assertEquals(BuildConfig.SENTRY_CLIENT_NAME, sdkVersion.name)
        assertEquals(BuildConfig.VERSION_NAME, sdkVersion.version)

        assertTrue(sdkVersion.packages!!.any {
            it.name == "maven:sentry-android-core"
            it.version == BuildConfig.VERSION_NAME
        })

        assertTrue(sdkVersion.packages!!.any {
            it.name == "maven:sentry-core"
            it.version == BuildConfig.VERSION_NAME
        })
    }
}
