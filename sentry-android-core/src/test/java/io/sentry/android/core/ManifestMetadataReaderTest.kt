package io.sentry.android.core

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import io.sentry.core.ILogger
import io.sentry.core.SentryLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManifestMetadataReaderTest {

    @Test
    fun `isAutoInit won't throw exception`() {
        // tests for the returned boolean are in SentryInitProviderTest
        val logger = mock<ILogger>()
        val context = ContextUtilsTest.mockMetaData(metaData = Bundle())
        assertTrue(ManifestMetadataReader.isAutoInit(context, logger))
        verify(logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `applyMetadata won't throw exception`() {
        // tests for the returned boolean are in SentryInitProviderTest
        val options = SentryAndroidOptions()

        val context = ContextUtilsTest.createMockContext()
        ManifestMetadataReader.applyMetadata(context, options)
        val logger = mock<ILogger>()
        options.setLogger(logger)
        verify(logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `applyMetadata reads sampleRate from metadata`() {
        // Arrange
        val options = SentryAndroidOptions()
        val expectedSampleRate = 0.99

        val bundle = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = bundle)
        bundle.putDouble(ManifestMetadataReader.SAMPLE_RATE, expectedSampleRate)

        // Act
        ManifestMetadataReader.applyMetadata(mockContext, options)

        // Assert
        assertEquals(expectedSampleRate, options.sampleRate)
    }

    @Test
    fun `applyMetadata does not override sampleRate from options`() {
        // Arrange
        val expectedSampleRate = 0.99
        val options = SentryAndroidOptions()
        options.sampleRate = expectedSampleRate

        val bundle = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = bundle)
        bundle.putDouble(ManifestMetadataReader.SAMPLE_RATE, 0.1)

        // Act
        ManifestMetadataReader.applyMetadata(mockContext, options)

        // Assert
        assertEquals(expectedSampleRate, options.sampleRate)
    }

    @Test
    fun `applyMetadata without specifying sampleRate, stays null`() {
        // Arrange
        val options = SentryAndroidOptions()
        val bundle = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(mockContext, options)

        // Assert
        assertNull(options.sampleRate)
    }

    @Test
    fun `applyMetadata reads session tracking to options`() {
        // Arrange
        val options = SentryAndroidOptions()
        val bundle = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = bundle)
        bundle.putBoolean(ManifestMetadataReader.SESSION_TRACKING_ENABLE, true)

        // Act
        ManifestMetadataReader.applyMetadata(mockContext, options)

        // Assert
        assertTrue(options.isEnableSessionTracking)
    }

    @Test
    fun `applyMetadata reads session tracking and keep default value if not found`() {
        // Arrange
        val options = SentryAndroidOptions()
        val bundle = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(mockContext, options)

        // Assert
        assertFalse(options.isEnableSessionTracking)
    }

    @Test
    fun `applyMetadata reads environment to options`() {
        // Arrange
        val options = SentryAndroidOptions()
        val bundle = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = bundle)
        bundle.putString(ManifestMetadataReader.ENVIRONMENT, "env")

        // Act
        ManifestMetadataReader.applyMetadata(mockContext, options)

        // Assert
        assertEquals("env", options.environment)
    }

    @Test
    fun `applyMetadata reads environment and keep default value if not found`() {
        // Arrange
        val options = SentryAndroidOptions()
        val bundle = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(mockContext, options)

        // Assert
        assertNull(options.environment)
    }

    @Test
    fun `applyMetadata reads release to options`() {
        // Arrange
        val options = SentryAndroidOptions()
        val bundle = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = bundle)
        bundle.putString(ManifestMetadataReader.RELEASE, "release")

        // Act
        ManifestMetadataReader.applyMetadata(mockContext, options)

        // Assert
        assertEquals("release", options.release)
    }

    @Test
    fun `applyMetadata reads release and keep default value if not found`() {
        // Arrange
        val options = SentryAndroidOptions()
        val bundle = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(mockContext, options)

        // Assert
        assertNull(options.release)
    }

    @Test
    fun `applyMetadata reads session tracking interval to options`() {
        // Arrange
        val options = SentryAndroidOptions()
        val bundle = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = bundle)
        bundle.putInt(ManifestMetadataReader.SESSION_TRACKING_TIMEOUT_INTERVAL_MILLIS, 1000)

        // Act
        ManifestMetadataReader.applyMetadata(mockContext, options)

        // Assert
        assertEquals(1000.toLong(), options.sessionTrackingIntervalMillis)
    }

    @Test
    fun `applyMetadata reads session tracking interval and keep default value if not found`() {
        // Arrange
        val options = SentryAndroidOptions()
        val bundle = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(mockContext, options)

        // Assert
        assertEquals(30000.toLong(), options.sessionTrackingIntervalMillis)
    }

    @Test
    fun `applyMetadata reads anr deprecated interval to options`() {
        // Arrange
        val options = SentryAndroidOptions()
        val bundle = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = bundle)
        bundle.putInt(ManifestMetadataReader.ANR_TIMEOUT_INTERVAL_MILLS, 1000)

        // Act
        ManifestMetadataReader.applyMetadata(mockContext, options)

        // Assert
        assertEquals(1000.toLong(), options.anrTimeoutIntervalMillis)
    }

    @Test
    fun `applyMetadata reads anr interval to options`() {
        // Arrange
        val options = SentryAndroidOptions()
        val bundle = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = bundle)
        bundle.putInt(ManifestMetadataReader.ANR_TIMEOUT_INTERVAL_MILLIS, 1000)

        // Act
        ManifestMetadataReader.applyMetadata(mockContext, options)

        // Assert
        assertEquals(1000.toLong(), options.anrTimeoutIntervalMillis)
    }

    @Test
    fun `applyMetadata reads activity breadcrumbs to options`() {
        // Arrange
        val options = SentryAndroidOptions()
        val bundle = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = bundle)
        bundle.putBoolean(ManifestMetadataReader.BREADCRUMBS_ACTIVITY_LIFECYCLE_ENABLE, false)

        // Act
        ManifestMetadataReader.applyMetadata(mockContext, options)

        // Assert
        assertFalse(options.isEnableActivityLifecycleBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads activity breadcrumbs and keep default value if not found`() {
        // Arrange
        val options = SentryAndroidOptions()
        val bundle = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(mockContext, options)

        // Assert
        assertTrue(options.isEnableActivityLifecycleBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads app lifecycle breadcrumbs to options`() {
        // Arrange
        val options = SentryAndroidOptions()
        val bundle = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = bundle)
        bundle.putBoolean(ManifestMetadataReader.BREADCRUMBS_APP_LIFECYCLE_ENABLE, false)

        // Act
        ManifestMetadataReader.applyMetadata(mockContext, options)

        // Assert
        assertFalse(options.isEnableAppLifecycleBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads app lifecycle breadcrumbs and keep default value if not found`() {
        // Arrange
        val options = SentryAndroidOptions()
        val bundle = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(mockContext, options)

        // Assert
        assertTrue(options.isEnableAppLifecycleBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads system events breadcrumbs to options`() {
        // Arrange
        val options = SentryAndroidOptions()
        val bundle = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = bundle)
        bundle.putBoolean(ManifestMetadataReader.BREADCRUMBS_SYSTEM_EVENTS_ENABLE, false)

        // Act
        ManifestMetadataReader.applyMetadata(mockContext, options)

        // Assert
        assertFalse(options.isEnableSystemEventBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads system events breadcrumbs and keep default value if not found`() {
        // Arrange
        val options = SentryAndroidOptions()
        val bundle = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(mockContext, options)

        // Assert
        assertTrue(options.isEnableSystemEventBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads app components breadcrumbs to options`() {
        // Arrange
        val options = SentryAndroidOptions()
        val bundle = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = bundle)
        bundle.putBoolean(ManifestMetadataReader.BREADCRUMBS_APP_COMPONENTS_ENABLE, false)

        // Act
        ManifestMetadataReader.applyMetadata(mockContext, options)

        // Assert
        assertFalse(options.isEnableAppComponentBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads app components breadcrumbs and keep default value if not found`() {
        // Arrange
        val options = SentryAndroidOptions()
        val bundle = Bundle()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(mockContext, options)

        // Assert
        assertTrue(options.isEnableAppComponentBreadcrumbs)
    }
}
