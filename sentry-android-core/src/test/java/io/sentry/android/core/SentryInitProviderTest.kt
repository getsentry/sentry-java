package io.sentry.android.core

import android.content.pm.ProviderInfo
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.mock
import io.sentry.core.ILogger
import io.sentry.core.InvalidDsnException
import io.sentry.core.Sentry
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SentryInitProviderTest {
    private var sentryInitProvider = SentryInitProvider()

    @BeforeTest
    fun `set up`() {
        Sentry.close()
    }

    @Test
    fun `when missing applicationId, SentryInitProvider throws`() {
        val providerInfo = ProviderInfo()

        val mockContext = ContextUtils.createMockContext()
        providerInfo.authority = SentryInitProvider::class.java.name
        assertFailsWith<IllegalStateException> { sentryInitProvider.attachInfo(mockContext, providerInfo) }
    }

    @Test
    fun `when applicationId is defined, dsn in meta-data, SDK initializes`() {
        val providerInfo = ProviderInfo()

        assertFalse(Sentry.isEnabled())
        providerInfo.authority = AUTHORITY

        val metaData = Bundle()
        val mockContext = ContextUtils.mockMetaData(metaData = metaData)

        metaData.putString(ManifestMetadataReader.DSN, "https://key@sentry.io/123")

        sentryInitProvider.attachInfo(mockContext, providerInfo)

        assertTrue(Sentry.isEnabled())
    }

    @Test
    fun `when applicationId is defined, dsn in meta-data is empty, SDK doesnt initialize`() {
        val providerInfo = ProviderInfo()

        assertFalse(Sentry.isEnabled())
        providerInfo.authority = AUTHORITY

        val metaData = Bundle()
        val mockContext = ContextUtils.mockMetaData(metaData = metaData)

        metaData.putString(ManifestMetadataReader.DSN, "")

        sentryInitProvider.attachInfo(mockContext, providerInfo)

        assertFalse(Sentry.isEnabled())
    }

    @Test
    fun `when applicationId is defined, dsn in meta-data is not set or null, SDK throws exception`() {
        val providerInfo = ProviderInfo()

        assertFalse(Sentry.isEnabled())
        providerInfo.authority = AUTHORITY

        val metaData = Bundle()
        val mockContext = ContextUtils.mockMetaData(metaData = metaData)

        metaData.putString(ManifestMetadataReader.DSN, null)

        assertFailsWith<IllegalArgumentException> { sentryInitProvider.attachInfo(mockContext, providerInfo) }
    }

    @Test
    fun `when applicationId is defined, dsn in meta-data is invalid, SDK should throw an error`() {
        val providerInfo = ProviderInfo()

        assertFalse(Sentry.isEnabled())
        providerInfo.authority = AUTHORITY

        val metaData = Bundle()
        val mockContext = ContextUtils.mockMetaData(metaData = metaData)

        metaData.putString(ManifestMetadataReader.DSN, "invalid dsn")

        assertFailsWith<InvalidDsnException> { sentryInitProvider.attachInfo(mockContext, providerInfo) }
    }

    @Test
    fun `when applicationId is defined, auto-init in meta-data is set to false, SDK doesnt initialize`() {
        val providerInfo = ProviderInfo()

        assertFalse(Sentry.isEnabled())
        providerInfo.authority = AUTHORITY

        val metaData = Bundle()
        val mockContext = ContextUtils.mockMetaData(metaData = metaData)

        metaData.putBoolean(ManifestMetadataReader.AUTO_INIT, false)

        sentryInitProvider.attachInfo(mockContext, providerInfo)

        assertFalse(Sentry.isEnabled())
    }

    @Test
    fun `when App context is null, do nothing`() {
        val providerInfo = ProviderInfo()

        assertFalse(Sentry.isEnabled())
        providerInfo.authority = AUTHORITY

        sentryInitProvider.attachInfo(null, providerInfo)

        assertFalse(Sentry.isEnabled())
    }

    @Test
    fun `when applicationId is defined, ndk in meta-data is set to false, NDK doesnt initialize`() {
        val sentryOptions = SentryAndroidOptions()
        val mockLogger = mock<ILogger>()

        val metaData = Bundle()
        val mockContext = ContextUtils.mockMetaData(metaData = metaData)
        metaData.putBoolean(ManifestMetadataReader.NDK_ENABLE, false)

        AndroidOptionsInitializer.init(sentryOptions, mockContext, mockLogger)

        assertFalse(sentryOptions.isEnableNdk)
    }

    companion object {
        private const val AUTHORITY = "io.sentry.sample.SentryInitProvider"
    }
}
