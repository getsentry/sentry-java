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
        val context = ContextUtils.mockMetaData(metaData = Bundle())
        assertTrue(ManifestMetadataReader.isAutoInit(context, logger))
        verify(logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `applyMetadata won't throw exception`() {
        // tests for the returned boolean are in SentryInitProviderTest
        val options = SentryAndroidOptions()

        val context = ContextUtils.createMockContext()
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
        val mockContext = ContextUtils.mockMetaData(metaData = bundle)
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
        val mockContext = ContextUtils.mockMetaData(metaData = bundle)
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
        val mockContext = ContextUtils.mockMetaData(metaData = bundle)

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
        val mockContext = ContextUtils.mockMetaData(metaData = bundle)
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
        val mockContext = ContextUtils.mockMetaData(metaData = bundle)

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
        val mockContext = ContextUtils.mockMetaData(metaData = bundle)
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
        val mockContext = ContextUtils.mockMetaData(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(mockContext, options)

        // Assert
        assertNull(options.environment)
    }

    @Test
    fun `applyMetadata reads session tracking interval to options`() {
        // Arrange
        val options = SentryAndroidOptions()
        val bundle = Bundle()
        val mockContext = ContextUtils.mockMetaData(metaData = bundle)
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
        val mockContext = ContextUtils.mockMetaData(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(mockContext, options)

        // Assert
        assertEquals(30000.toLong(), options.sessionTrackingIntervalMillis)
    }
}
