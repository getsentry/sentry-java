package io.sentry.android.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.InvalidDsnException
import io.sentry.core.Sentry
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SentryInitProviderTest {
    private var sentryInitProvider = SentryInitProvider()

    private lateinit var context: Context

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
        Sentry.close()
    }

    @Test
    fun `when missing applicationId, SentryInitProvider throws`() {
        val providerInfo = ProviderInfo()

        providerInfo.authority = SentryInitProvider::class.java.name
        assertFailsWith<IllegalStateException> { sentryInitProvider.attachInfo(context, providerInfo) }
    }

    @Test
    fun `when applicationId is defined, dsn in meta-data, SDK initializes`() {
        val providerInfo = ProviderInfo()

        assertFalse(Sentry.isEnabled())
        providerInfo.authority = AUTHORITY

        val mockContext = createMockContext()
        val metaData = Bundle()
        mockMetaData(mockContext, metaData)

        metaData.putString(ManifestMetadataReader.DSN_KEY, "https://key@sentry.io/123")

        sentryInitProvider.attachInfo(mockContext, providerInfo)

        assertTrue(Sentry.isEnabled())
    }

    @Test
    fun `when applicationId is defined, dsn in meta-data is empty, SDK doesnt initialize`() {
        val providerInfo = ProviderInfo()

        assertFalse(Sentry.isEnabled())
        providerInfo.authority = AUTHORITY

        val mockContext = createMockContext()
        val metaData = Bundle()
        mockMetaData(mockContext, metaData)

        metaData.putString(ManifestMetadataReader.DSN_KEY, "")

        sentryInitProvider.attachInfo(mockContext, providerInfo)

        assertFalse(Sentry.isEnabled())
    }

    @Test
    fun `when applicationId is defined, dsn in meta-data is not set or null, SDK doesnt initialize`() {
        val providerInfo = ProviderInfo()

        assertFalse(Sentry.isEnabled())
        providerInfo.authority = AUTHORITY

        val mockContext = createMockContext()
        val metaData = Bundle()
        mockMetaData(mockContext, metaData)

        metaData.putString(ManifestMetadataReader.DSN_KEY, null)

        sentryInitProvider.attachInfo(mockContext, providerInfo)

        assertFalse(Sentry.isEnabled())
    }

    @Test
    fun `when applicationId is defined, dsn in meta-data is invalid, SDK should throw an error`() {
        val providerInfo = ProviderInfo()

        assertFalse(Sentry.isEnabled())
        providerInfo.authority = AUTHORITY

        val mockContext = createMockContext()
        val metaData = Bundle()
        mockMetaData(mockContext, metaData)

        metaData.putString(ManifestMetadataReader.DSN_KEY, "invalid dsn")

        assertFailsWith<InvalidDsnException> { sentryInitProvider.attachInfo(mockContext, providerInfo) }
    }

    @Test
    fun `when applicationId is defined, auto-init in meta-data is set to false, SDK doesnt initialize`() {
        val providerInfo = ProviderInfo()

        assertFalse(Sentry.isEnabled())
        providerInfo.authority = AUTHORITY

        val mockContext = createMockContext()
        val metaData = Bundle()
        mockMetaData(mockContext, metaData)

        metaData.putBoolean(ManifestMetadataReader.AUTO_INIT, false)

        sentryInitProvider.attachInfo(mockContext, providerInfo)

        assertFalse(Sentry.isEnabled())
    }

    private fun mockMetaData(mockContext: Context, metaData: Bundle) {
        val mockPackageManager: PackageManager = mock()
        val mockApplicationInfo: ApplicationInfo = mock()

        whenever(mockContext.packageName).thenReturn("io.sentry.sample.test")
        whenever(mockContext.packageManager).thenReturn(mockPackageManager)
        whenever(mockPackageManager.getApplicationInfo(mockContext.packageName, PackageManager.GET_META_DATA)).thenReturn(mockApplicationInfo)

        mockApplicationInfo.metaData = metaData
    }

    private fun createMockContext(): Context {
        val mockContext = mock<Context> {
            on { applicationContext } doReturn context
        }
        whenever(mockContext.cacheDir).thenReturn(File("${File.separator}cache"))
        return mockContext
    }

    companion object {
        private const val AUTHORITY = "io.sentry.sample.SentryInitProvider"
    }
}
