package io.sentry.android.core

import io.sentry.ITransaction
import io.sentry.ITransactionProfiler
import io.sentry.NoOpTransactionProfiler
import io.sentry.PerformanceCollectionData
import io.sentry.ProfilingTraceData
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.protocol.DebugImage
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SentryAndroidOptionsTest {

    @Before
    fun clearIntegrationPackageStorage() {
        SentryIntegrationPackageStorage.getInstance().clearStorage()
    }

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
        val sentryOptions = SentryAndroidOptions().apply {
            setTransactionProfiler(CustomTransactionProfiler())
        }
        assertNotNull(sentryOptions.transactionProfiler)
    }

    @Test
    fun `set transactionProfiler to null sets it to noop`() {
        val sentryOptions = SentryAndroidOptions().apply {
            setTransactionProfiler(null)
        }
        assertEquals(sentryOptions.transactionProfiler, NoOpTransactionProfiler.getInstance())
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
    fun `attach screenshot is not added to the IntegrationList by default`() {
        val sentryOptions = SentryAndroidOptions()

        assertFalse(sentryOptions.isAttachScreenshot)
        assertFalse(sentryOptions.sdkVersion!!.integrationSet.contains("AttachScreenshot"))
    }

    @Test
    fun `attach screenshot is added to the IntegrationList if enabled`() {
        val sentryOptions = SentryAndroidOptions()

        sentryOptions.isAttachScreenshot = true

        assertTrue(sentryOptions.isAttachScreenshot)
        assertTrue(sentryOptions.sdkVersion!!.integrationSet.contains("AttachScreenshot"))
    }

    @Test
    fun `view hierarchy integration is not added to the IntegrationList by default`() {
        val sentryOptions = SentryAndroidOptions()

        assertFalse(sentryOptions.isAttachViewHierarchy)
        assertFalse(sentryOptions.sdkVersion!!.integrationSet.contains("AttachViewHierarchy"))
    }

    @Test
    fun `view hierarchy integration is added to the IntegrationList if enabled`() {
        val sentryOptions = SentryAndroidOptions()

        sentryOptions.isAttachViewHierarchy = true

        assertTrue(sentryOptions.isAttachViewHierarchy)
        assertTrue(sentryOptions.sdkVersion!!.integrationSet.contains("AttachViewHierarchy"))
    }

    private class CustomDebugImagesLoader : IDebugImagesLoader {
        override fun loadDebugImages(): List<DebugImage>? = null
        override fun clearDebugImages() {}
    }

    private class CustomTransactionProfiler : ITransactionProfiler {
        override fun onTransactionStart(transaction: ITransaction) {}
        override fun onTransactionFinish(
            transaction: ITransaction,
            performanceCollectionData: List<PerformanceCollectionData>?
        ): ProfilingTraceData? = null

        override fun close() {}
    }
}
