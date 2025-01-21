package io.sentry.android.core

import io.sentry.ITransactionProfiler
import io.sentry.NoOpTransactionProfiler
import io.sentry.protocol.DebugImage
import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

        assertTrue(
            sdkVersion.packageSet.any {
                it.name == "maven:io.sentry:sentry-android-core" &&
                    it.version == BuildConfig.VERSION_NAME
            }
        )

        assertTrue(
            sdkVersion.packageSet.any {
                it.name == "maven:io.sentry:sentry" &&
                    it.version == BuildConfig.VERSION_NAME
            }
        )
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

    @Test
    fun `init should set NoOpTransactionProfiler`() {
        val sentryOptions = SentryAndroidOptions()
        assertEquals(NoOpTransactionProfiler.getInstance(), sentryOptions.transactionProfiler)
    }

    @Test
    fun `set transactionProfiler accepts non null value`() {
        val profiler = mock<ITransactionProfiler>()
        val sentryOptions = SentryAndroidOptions().apply {
            setTransactionProfiler(profiler)
        }
        assertEquals(profiler, sentryOptions.transactionProfiler)
    }

    @Test
    fun `set transactionProfiler to null is ignored`() {
        val sentryOptions = SentryAndroidOptions().apply {
            setTransactionProfiler(null)
        }
        assertEquals(NoOpTransactionProfiler.getInstance(), sentryOptions.transactionProfiler)
    }

    @Test
    fun `set transactionProfiler multiple times is ignored`() {
        val profiler = mock<ITransactionProfiler>()
        val sentryOptions = SentryAndroidOptions().apply {
            setTransactionProfiler(profiler)
            setTransactionProfiler(mock())
        }
        assertEquals(profiler, sentryOptions.transactionProfiler)
    }

    @Test
    fun `enable scope sync by default for Android`() {
        val sentryOptions = SentryAndroidOptions()

        assertTrue(sentryOptions.isEnableScopeSync)
    }

    @Test
    fun `attach screenshots disabled by default for Android`() {
        val sentryOptions = SentryAndroidOptions()

        assertFalse(sentryOptions.isAttachScreenshot)
    }

    @Test
    fun `user interaction tracing disabled by default for Android`() {
        val sentryOptions = SentryAndroidOptions()

        assertFalse(sentryOptions.isEnableUserInteractionTracing)
    }

    @Test
    fun `attach view hierarchy is disabled by default for Android`() {
        val sentryOptions = SentryAndroidOptions()

        assertFalse(sentryOptions.isAttachViewHierarchy)
    }

    @Test
    fun `native sdk name is null by default`() {
        val sentryOptions = SentryAndroidOptions()
        assertNull(sentryOptions.nativeSdkName)
    }

    @Test
    fun `native sdk name can be properly set`() {
        val sentryOptions = SentryAndroidOptions()
        sentryOptions.nativeSdkName = "test_ndk_name"
        assertEquals("test_ndk_name", sentryOptions.nativeSdkName)
    }

    @Test
    fun `native sdk name can be properly set to null`() {
        val sentryOptions = SentryAndroidOptions()
        sentryOptions.nativeSdkName = "test_ndk_name"
        sentryOptions.nativeSdkName = null
        assertNull(sentryOptions.nativeSdkName)
    }

    @Test
    fun `enableScopeSync can be properly disabled`() {
        val options = SentryAndroidOptions()
        options.isEnableScopeSync = false

        assertFalse(options.isEnableScopeSync)
    }

    @Test
    fun `performance v2 is enabled by default`() {
        val sentryOptions = SentryAndroidOptions()
        assertTrue(sentryOptions.isEnablePerformanceV2)
    }

    @Test
    fun `performance v2 can be disabled`() {
        val sentryOptions = SentryAndroidOptions()
        sentryOptions.isEnablePerformanceV2 = false
        assertFalse(sentryOptions.isEnablePerformanceV2)
    }

    fun `when options is initialized, enableScopeSync is enabled by default`() {
        assertTrue(SentryAndroidOptions().isEnableScopeSync)
    }

    @Test
    fun `ndk handler option defaults to default strategy`() {
        val sentryOptions = SentryAndroidOptions()
        assertEquals(NdkHandlerStrategy.SENTRY_HANDLER_STRATEGY_DEFAULT.value, sentryOptions.ndkHandlerStrategy)
    }

    @Test
    fun `ndk handler strategy option can be changed`() {
        val sentryOptions = SentryAndroidOptions()
        sentryOptions.setNativeHandlerStrategy(NdkHandlerStrategy.SENTRY_HANDLER_STRATEGY_CHAIN_AT_START)
        assertEquals(NdkHandlerStrategy.SENTRY_HANDLER_STRATEGY_CHAIN_AT_START.value, sentryOptions.ndkHandlerStrategy)
    }

    private class CustomDebugImagesLoader : IDebugImagesLoader {
        override fun loadDebugImages(): List<DebugImage>? = null
        override fun loadDebugImagesForAddresses(addresses: Set<Long>?): Set<DebugImage>? = null;

        override fun clearDebugImages() {}
    }
}
