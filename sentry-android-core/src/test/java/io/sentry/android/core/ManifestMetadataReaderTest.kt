package io.sentry.android.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyVararg
import com.nhaarman.mockitokotlin2.argWhere
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.ILogger
import io.sentry.core.SentryLevel
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManifestMetadataReaderTest {

    private lateinit var context: Context

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `isAutoInit won't throw exception`() {
        // tests for the returned boolean are in SentryInitProviderTest
        val logger = mock<ILogger>()
        assertTrue(ManifestMetadataReader.isAutoInit(context, logger))
        verify(logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `applyMetadata won't throw exception`() {
        // tests for the returned boolean are in SentryInitProviderTest
        val options = SentryAndroidOptions()

        ManifestMetadataReader.applyMetadata(context, options)
        val logger = mock<ILogger>()
        options.setLogger(logger)
        verify(logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `applyMetadata reads sampleRate from metadata`() {
        // Arrange
        val options = SentryAndroidOptions()
        val expectedPackageName = "io.sentry.test"
        val expectedSampleRate = 0.99
        val contextMock: Context = mock()
        whenever(contextMock.packageName).thenReturn(expectedPackageName)
        val bundle: Bundle = mock()
        whenever(bundle.getDouble(eq(ManifestMetadataReader.SAMPLE_RATE), anyVararg())).thenReturn(expectedSampleRate)
        val packageManagerMock: PackageManager = mock()
        val applicationInfo: ApplicationInfo = mock()
        applicationInfo.metaData = bundle
        whenever(packageManagerMock.getApplicationInfo(argWhere { it == expectedPackageName }, eq(PackageManager.GET_META_DATA))).thenReturn(applicationInfo)
        whenever(contextMock.packageManager).thenReturn(packageManagerMock)

        // Act
        ManifestMetadataReader.applyMetadata(contextMock, options)

        // Assert
        assertEquals(expectedSampleRate, options.sampleRate)
    }

    @Test
    fun `applyMetadata does not override sampleRate from options`() {
        // Arrange
        val expectedSampleRate = 0.99
        val options = SentryAndroidOptions()
        options.sampleRate = expectedSampleRate
        val expectedPackageName = "io.sentry.test"
        val contextMock: Context = mock()
        whenever(contextMock.packageName).thenReturn(expectedPackageName)
        val bundle: Bundle = mock()
        whenever(bundle.getDouble(eq(ManifestMetadataReader.SAMPLE_RATE), anyVararg())).thenReturn(0.1)
        val packageManagerMock: PackageManager = mock()
        val applicationInfo: ApplicationInfo = mock()
        applicationInfo.metaData = bundle
        whenever(packageManagerMock.getApplicationInfo(argWhere { it == expectedPackageName }, eq(PackageManager.GET_META_DATA))).thenReturn(applicationInfo)
        whenever(contextMock.packageManager).thenReturn(packageManagerMock)

        // Act
        ManifestMetadataReader.applyMetadata(contextMock, options)

        // Assert
        assertEquals(expectedSampleRate, options.sampleRate)
    }

    @Test
    fun `applyMetadata without specifying sampleRate, stays null`() {
        // Arrange
        val options = SentryAndroidOptions()
        val expectedPackageName = "io.sentry.test"
        val contextMock: Context = mock()
        whenever(contextMock.packageName).thenReturn(expectedPackageName)
        val bundle: Bundle = mock()
        val packageManagerMock: PackageManager = mock()
        val applicationInfo: ApplicationInfo = mock()
        applicationInfo.metaData = bundle
        whenever(packageManagerMock.getApplicationInfo(argWhere { it == expectedPackageName }, eq(PackageManager.GET_META_DATA))).thenReturn(applicationInfo)
        whenever(contextMock.packageManager).thenReturn(packageManagerMock)

        // Act
        ManifestMetadataReader.applyMetadata(contextMock, options)

        // Assert
        assertNull(options.sampleRate)
    }
}
