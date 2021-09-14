package io.sentry.android.core

import io.sentry.protocol.DebugImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SentryAndroidOptionsTest {

    @Test
    fun `init should set clientName`() {
        val sentryOptions = SentryAndroidOptions()

        val clientName = "${BuildConfig.SENTRY_ANDROID_SDK_NAME}/${BuildConfig.VERSION_NAME}"

        assertEquals(clientName, sentryOptions.sentryClientName)
    }

    @Test
    fun `init should set SdkVersion`() {
        val sentryOptions = SentryAndroidOptions()
        assertNotNull(sentryOptions.sdkVersion)
        val sdkVersion = sentryOptions.sdkVersion!!

        assertEquals(BuildConfig.SENTRY_ANDROID_SDK_NAME, sdkVersion.name)
        assertEquals(BuildConfig.VERSION_NAME, sdkVersion.version)

        assertTrue(sdkVersion.packages!!.any {
            it.name == "maven:io.sentry:sentry-android-core" &&
            it.version == BuildConfig.VERSION_NAME
        })

        assertTrue(sdkVersion.packages!!.any {
            it.name == "maven:io.sentry:sentry" &&
            it.version == BuildConfig.VERSION_NAME
        })
    }

    @Test
    fun `init should set NoOpDebugImagesLoader`() {
        val sentryOptions = SentryAndroidOptions()
        assertEquals(NoOpDebugImagesLoader.getInstance(), sentryOptions.debugImagesLoader)
    }

    @Test
    fun `set debugImagesLoader accepts non null value`() {
        val sentryOptions = SentryAndroidOptions().apply {
            debugImagesLoader = CustomDebugImagesLoader()
        }
        assertNotNull(sentryOptions.debugImagesLoader)
    }

    private class CustomDebugImagesLoader : IDebugImagesLoader {
        override fun loadDebugImages(): List<DebugImage>? = null
        override fun clearDebugImages() {}
    }
}
