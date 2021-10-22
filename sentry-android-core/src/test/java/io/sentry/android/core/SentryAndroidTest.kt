package io.sentry.android.core

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import io.sentry.ILogger
import io.sentry.Sentry
import io.sentry.SentryLevel
import org.junit.runner.RunWith
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SentryAndroidTest {

    @BeforeTest
    fun `set up`() {
        Sentry.close()
        AppStartState.getInstance().resetInstance()
    }

    @Test
    fun `when auto-init is disabled and user calls init manually, SDK initializes`() {
        assertFalse(Sentry.isEnabled())

        val metaData = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = metaData)

        metaData.putString(ManifestMetadataReader.DSN, "https://key@sentry.io/123")
        metaData.putBoolean(ManifestMetadataReader.AUTO_INIT, false)

        SentryAndroid.init(mockContext)

        assertTrue(Sentry.isEnabled())
    }

    @Test
    fun `when auto-init is disabled and user calls init manually with a logger, SDK initializes`() {
        assertFalse(Sentry.isEnabled())

        val metaData = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = metaData)

        metaData.putString(ManifestMetadataReader.DSN, "https://key@sentry.io/123")
        metaData.putBoolean(ManifestMetadataReader.AUTO_INIT, false)

        val logger = mock<ILogger>()

        SentryAndroid.init(mockContext, logger)

        assertTrue(Sentry.isEnabled())
    }

    @Test
    fun `when auto-init is disabled and user calls init manually with configuration handler, options should be set`() {
        assertFalse(Sentry.isEnabled())

        val metaData = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = metaData)

        metaData.putString(ManifestMetadataReader.DSN, "https://key@sentry.io/123")
        metaData.putBoolean(ManifestMetadataReader.AUTO_INIT, false)

        var refOptions: SentryAndroidOptions? = null
        SentryAndroid.init(mockContext) { options ->
            options.anrTimeoutIntervalMillis = 3000
            refOptions = options
        }

        assertEquals(3000, refOptions!!.anrTimeoutIntervalMillis)

        assertTrue(Sentry.isEnabled())
    }

    @Test
    fun `init won't throw exception`() {
        val metaData = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = metaData)
        metaData.putString(ManifestMetadataReader.DSN, "https://key@sentry.io/123")

        val logger = mock<ILogger>()
        SentryAndroid.init(mockContext, logger)
        verify(logger, never()).log(eq(SentryLevel.FATAL), any<String>(), any())
    }

    @Test
    fun `set app start if provider is disabled`() {
        val metaData = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = metaData)
        metaData.putString(ManifestMetadataReader.DSN, "https://key@sentry.io/123")

        SentryAndroid.init(mockContext, mock<ILogger>())

        // done by ActivityLifecycleIntegration so forcing it here
        AppStartState.getInstance().setAppStartEnd()

        assertNotNull(AppStartState.getInstance().appStartInterval)
    }
}
