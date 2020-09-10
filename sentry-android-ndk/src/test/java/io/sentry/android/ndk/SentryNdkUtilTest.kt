package io.sentry.android.ndk

import io.sentry.SentryOptions
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryNdkUtilTest {

    @Test
    fun `SentryNdk adds the Ndk package into the package list`() {
        val options = SentryOptions()
        SentryNdkUtil.addPackage(options.sdkVersion)
        assertTrue(options.sdkVersion!!.packages!!.any {
            it.name == "maven:sentry-android-ndk"
            it.version == BuildConfig.VERSION_NAME
        })
    }

    @Test
    fun `SentryNdk do not add the Ndk package into the package list`() {
        val options = SentryOptions().apply {
            sdkVersion = null
        }
        SentryNdkUtil.addPackage(options.sdkVersion)

        assertNull(options.sdkVersion?.packages)
    }
}
