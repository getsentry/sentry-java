package io.sentry.android.core

import android.content.Context
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ILogger
import io.sentry.SentryLevel
import io.sentry.SentryReplayOptions
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ManifestMetadataReaderTest {

    private class Fixture {
        val logger = mock<ILogger>()
        val options = SentryAndroidOptions().apply {
            setLogger(logger)
        }
        val buildInfoProvider = mock<BuildInfoProvider>()

        fun getContext(metaData: Bundle = Bundle()): Context {
            return ContextUtilsTestHelper.mockMetaData(metaData = metaData)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `isAutoInit won't throw exception and is enabled by default`() {
        fixture.options.setDebug(true)
        val context = fixture.getContext()

        assertTrue(ManifestMetadataReader.isAutoInit(context, fixture.logger))
        verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `Disables auto init mode`() {
        val bundle = bundleOf(ManifestMetadataReader.AUTO_INIT to false)
        val context = fixture.getContext(metaData = bundle)

        assertFalse(ManifestMetadataReader.isAutoInit(context, fixture.logger))
    }

    @Test
    fun `applyMetadata won't throw exception`() {
        fixture.options.setDebug(true)
        val context = fixture.getContext()

        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `applyMetadata reads sampleRate from metadata`() {
        // Arrange
        val expectedSampleRate = 0.99f

        val bundle = bundleOf(ManifestMetadataReader.SAMPLE_RATE to expectedSampleRate)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(expectedSampleRate.toDouble(), fixture.options.sampleRate)
    }

    @Test
    fun `applyMetadata does not override sampleRate from options`() {
        // Arrange
        val expectedSampleRate = 0.99f
        fixture.options.sampleRate = expectedSampleRate.toDouble()
        val bundle = bundleOf(ManifestMetadataReader.SAMPLE_RATE to 0.1f)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(expectedSampleRate.toDouble(), fixture.options.sampleRate)
    }

    @Test
    fun `applyMetadata without specifying sampleRate, stays null`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertNull(fixture.options.sampleRate)
    }

    @Test
    fun `applyMetadata reads session tracking and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isEnableAutoSessionTracking)
    }

    @Test
    fun `applyMetadata reads auto session tracking to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.AUTO_SESSION_TRACKING_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isEnableAutoSessionTracking)
    }

    @Test
    fun `applyMetadata reads environment to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.ENVIRONMENT to "env")
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals("env", fixture.options.environment)
    }

    @Test
    fun `applyMetadata reads environment and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals("production", fixture.options.environment)
    }

    @Test
    fun `applyMetadata reads release to options`() {
        // Arrange

        val bundle = bundleOf(ManifestMetadataReader.RELEASE to "release")
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals("release", fixture.options.release)
    }

    @Test
    fun `applyMetadata reads release and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertNull(fixture.options.release)
    }

    @Test
    fun `applyMetadata reads session tracking interval to options`() {
        // Arrange

        val bundle = bundleOf(ManifestMetadataReader.SESSION_TRACKING_TIMEOUT_INTERVAL_MILLIS to 1000)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(1000.toLong(), fixture.options.sessionTrackingIntervalMillis)
    }

    @Test
    fun `applyMetadata reads session tracking interval and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(30000.toLong(), fixture.options.sessionTrackingIntervalMillis)
    }

    @Test
    fun `applyMetadata reads anr interval to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.ANR_TIMEOUT_INTERVAL_MILLIS to 1000)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(1000.toLong(), fixture.options.anrTimeoutIntervalMillis)
    }

    @Test
    fun `applyMetadata reads anr interval to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(5000.toLong(), fixture.options.anrTimeoutIntervalMillis)
    }

    @Test
    fun `applyMetadata reads anr attach thread dump to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.ANR_ATTACH_THREAD_DUMPS to true)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(true, fixture.options.isAttachAnrThreadDump)
    }

    @Test
    fun `applyMetadata reads anr attach thread dump to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(false, fixture.options.isAttachAnrThreadDump)
    }

    @Test
    fun `applyMetadata reads activity breadcrumbs to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.BREADCRUMBS_ACTIVITY_LIFECYCLE_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isEnableActivityLifecycleBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads activity breadcrumbs and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isEnableActivityLifecycleBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads app lifecycle breadcrumbs to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.BREADCRUMBS_APP_LIFECYCLE_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isEnableAppLifecycleBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads app lifecycle breadcrumbs and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isEnableAppLifecycleBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads system events breadcrumbs to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.BREADCRUMBS_SYSTEM_EVENTS_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isEnableSystemEventBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads system events breadcrumbs and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isEnableSystemEventBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads network events breadcrumbs to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.BREADCRUMBS_NETWORK_EVENTS_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isEnableNetworkEventBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads network events breadcrumbs and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isEnableNetworkEventBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads app components breadcrumbs to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.BREADCRUMBS_APP_COMPONENTS_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isEnableAppComponentBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads app components breadcrumbs and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isEnableAppComponentBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads enableUncaughtExceptionHandler to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.UNCAUGHT_EXCEPTION_HANDLER_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isEnableUncaughtExceptionHandler)
    }

    @Test
    fun `applyMetadata reads enableUncaughtExceptionHandler and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isEnableUncaughtExceptionHandler)
    }

    @Test
    fun `applyMetadata reads attachThreads to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.ATTACH_THREADS to true)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isAttachThreads)
    }

    @Test
    fun `applyMetadata reads attachThreads and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isAttachThreads)
    }

    @Test
    fun `applyMetadata reads isDebug to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.DEBUG to true)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isDebug)
    }

    @Test
    fun `applyMetadata reads isDebug and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isDebug)
    }

    @Test
    fun `applyMetadata reads diagnosticLevel to options`() {
        // Arrange
        val bundle = bundleOf(
            ManifestMetadataReader.DEBUG to true,
            ManifestMetadataReader.DEBUG_LEVEL to "info"
        )
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(SentryLevel.INFO, fixture.options.diagnosticLevel)
    }

    @Test
    fun `applyMetadata reads diagnosticLevel to options and keeps default`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.DEBUG to true)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(SentryLevel.DEBUG, fixture.options.diagnosticLevel)
    }

    @Test
    fun `applyMetadata reads anrEnabled to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.ANR_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isAnrEnabled)
    }

    @Test
    fun `applyMetadata reads anrEnabled to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isAnrEnabled)
    }

    @Test
    fun `applyMetadata reads anrReportInDebug to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.ANR_REPORT_DEBUG to true)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isAnrReportInDebug)
    }

    @Test
    fun `applyMetadata reads anrReportInDebug to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isAnrReportInDebug)
    }

    @Test
    fun `applyMetadata reads DSN to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.DSN to "dsn")
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals("dsn", fixture.options.dsn)
    }

    @Test
    fun `applyMetadata reads DSN to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()
        fixture.options.dsn = "myOwnDsn"

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals("myOwnDsn", fixture.options.dsn)
    }

    @Test
    fun `applyMetadata reads enableNdk to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.NDK_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isEnableNdk)
    }

    @Test
    fun `applyMetadata reads enableNdk to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isEnableNdk)
    }

    @Test
    fun `applyMetadata reads SDK name from metadata`() {
        // Arrange
        val expectedValue = "custom.sdk"

        val bundle = bundleOf(ManifestMetadataReader.SDK_NAME to expectedValue)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(expectedValue, fixture.options.sdkVersion?.name)
    }

    @Test
    fun `applyMetadata reads SDK version from metadata`() {
        // Arrange
        val expectedValue = "1.2.3-alpha.0"

        val bundle = bundleOf(ManifestMetadataReader.SDK_VERSION to expectedValue)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(expectedValue, fixture.options.sdkVersion?.version)
    }

    @Test
    fun `applyMetadata reads enableScopeSync to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.NDK_SCOPE_SYNC_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isEnableScopeSync)
    }

    @Test
    fun `applyMetadata reads enableScopeSync to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isEnableScopeSync)
    }

    @Test
    fun `applyMetadata reads tracesSampleRate from metadata`() {
        // Arrange
        val expectedSampleRate = 0.99f
        val bundle = bundleOf(ManifestMetadataReader.TRACES_SAMPLE_RATE to expectedSampleRate)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(expectedSampleRate.toDouble(), fixture.options.tracesSampleRate)
    }

    @Test
    fun `applyMetadata does not override tracesSampleRate from options`() {
        // Arrange
        val expectedSampleRate = 0.99f
        fixture.options.tracesSampleRate = expectedSampleRate.toDouble()
        val bundle = bundleOf(ManifestMetadataReader.TRACES_SAMPLE_RATE to 0.1f)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(expectedSampleRate.toDouble(), fixture.options.tracesSampleRate)
    }

    @Test
    fun `applyMetadata without specifying tracesSampleRate, stays null`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertNull(fixture.options.tracesSampleRate)
    }

    @Test
    fun `applyMetadata reads enableAutoActivityLifecycleTracing to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.TRACES_ACTIVITY_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isEnableAutoActivityLifecycleTracing)
    }

    @Test
    fun `applyMetadata reads enableAutoActivityLifecycleTracing to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isEnableAutoActivityLifecycleTracing)
    }

    @Test
    fun `applyMetadata reads enableActivityLifecycleTracingAutoFinish to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.TRACES_ACTIVITY_AUTO_FINISH_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isEnableActivityLifecycleTracingAutoFinish)
    }

    @Test
    fun `applyMetadata reads enableActivityLifecycleTracingAutoFinish to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isEnableActivityLifecycleTracingAutoFinish)
    }

    @Test
    fun `applyMetadata reads traceSampling to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.TRACE_SAMPLING to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isTraceSampling)
    }

    @Test
    fun `applyMetadata reads traceSampling to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isTraceSampling)
    }

    @Test
    fun `applyMetadata reads profilesSampleRate from metadata`() {
        // Arrange
        val expectedSampleRate = 0.99f
        val bundle = bundleOf(ManifestMetadataReader.PROFILES_SAMPLE_RATE to expectedSampleRate)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(expectedSampleRate.toDouble(), fixture.options.profilesSampleRate)
    }

    @Test
    fun `applyMetadata does not override profilesSampleRate from options`() {
        // Arrange
        val expectedSampleRate = 0.99f
        fixture.options.profilesSampleRate = expectedSampleRate.toDouble()
        val bundle = bundleOf(ManifestMetadataReader.PROFILES_SAMPLE_RATE to 0.1f)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(expectedSampleRate.toDouble(), fixture.options.profilesSampleRate)
    }

    @Test
    fun `applyMetadata without specifying profilesSampleRate, stays null`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertNull(fixture.options.profilesSampleRate)
    }

    @Test
    fun `applyMetadata reads continuousProfilesSampleRate from metadata`() {
        // Arrange
        val expectedSampleRate = 0.99f
        val bundle = bundleOf(ManifestMetadataReader.CONTINUOUS_PROFILES_SAMPLE_RATE to expectedSampleRate)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(expectedSampleRate.toDouble(), fixture.options.continuousProfilesSampleRate)
    }

    @Test
    fun `applyMetadata does not override continuousProfilesSampleRate from options`() {
        // Arrange
        val expectedSampleRate = 0.99f
        fixture.options.continuousProfilesSampleRate = expectedSampleRate.toDouble()
        val bundle = bundleOf(ManifestMetadataReader.CONTINUOUS_PROFILES_SAMPLE_RATE to 0.1f)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(expectedSampleRate.toDouble(), fixture.options.continuousProfilesSampleRate)
    }

    @Test
    fun `applyMetadata without specifying continuousProfilesSampleRate, stays 1`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(1.0, fixture.options.continuousProfilesSampleRate)
    }

    @Test
    fun `applyMetadata reads tracePropagationTargets to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.TRACE_PROPAGATION_TARGETS to """localhost,^(http|https)://api\..*$""")
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(listOf("localhost", """^(http|https)://api\..*$"""), fixture.options.tracePropagationTargets)
    }

    @Test
    fun `applyMetadata reads null tracePropagationTargets and sets empty list`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.TRACE_PROPAGATION_TARGETS to null)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.tracePropagationTargets.isEmpty())
    }

    @Test
    fun `applyMetadata reads tracePropagationTargets to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.tracePropagationTargets.size == 1)
        assertTrue(fixture.options.tracePropagationTargets.first() == ".*")
    }

    @Test
    fun `applyMetadata reads proguardUuid to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.PROGUARD_UUID to "proguard-id")
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals("proguard-id", fixture.options.proguardUuid)
    }

    @Test
    fun `applyMetadata reads proguardUuid to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertNull(fixture.options.proguardUuid)
    }

    @Test
    fun `applyMetadata reads ui events breadcrumbs to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.BREADCRUMBS_USER_INTERACTION_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isEnableUserInteractionBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads ui events breadcrumbs and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isEnableUserInteractionBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads attach screenshots to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.ATTACH_SCREENSHOT to true)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isAttachScreenshot)
    }

    @Test
    fun `applyMetadata reads attach viewhierarchy to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.ATTACH_VIEW_HIERARCHY to true)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isAttachViewHierarchy)
    }

    @Test
    fun `applyMetadata reads attach screenshots and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isAttachScreenshot)
    }

    @Test
    fun `applyMetadata reads send client reports to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.CLIENT_REPORTS_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isSendClientReports)
    }

    @Test
    fun `applyMetadata reads send client reports and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isSendClientReports)
    }

    @Test
    fun `applyMetadata reads user interaction tracing to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.TRACES_UI_ENABLE to true)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isEnableUserInteractionTracing)
    }

    @Test
    fun `applyMetadata reads user interaction tracing and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isEnableUserInteractionTracing)
    }

    @Test
    fun `applyMetadata reads idleTimeout from metadata`() {
        // Arrange
        val expectedIdleTimeout = 1500
        val bundle = bundleOf(ManifestMetadataReader.IDLE_TIMEOUT to expectedIdleTimeout)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(expectedIdleTimeout.toLong(), fixture.options.idleTimeout)
    }

    @Test
    fun `applyMetadata without specifying idleTimeout, stays default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(3000L, fixture.options.idleTimeout)
    }

    @Test
    fun `applyMetadata reads collect ipc device info to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.COLLECT_ADDITIONAL_CONTEXT to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isCollectAdditionalContext)
    }

    @Test
    fun `applyMetadata reads collect ipc device info and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isCollectAdditionalContext)
    }

    @Test
    fun `applyMetadata reads send default pii and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isSendDefaultPii)
    }

    @Test
    fun `applyMetadata reads send default pii to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.SEND_DEFAULT_PII to true)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isSendDefaultPii)
    }

    @Test
    fun `applyMetadata reads frames tracking flag and keeps default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isEnableFramesTracking)
    }

    @Test
    fun `applyMetadata reads frames tracking and sets it to enabled if true`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.PERFORM_FRAMES_TRACKING to true)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isEnableFramesTracking)
    }

    @Test
    fun `applyMetadata reads frames tracking and sets it to disabled if false`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.PERFORM_FRAMES_TRACKING to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isEnableFramesTracking)
    }

    @Test
    fun `applyMetadata reads time-to-full-display tracking and sets it to enabled if true`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.TTFD_ENABLE to true)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isEnableTimeToFullDisplayTracing)
    }

    @Test
    fun `applyMetadata reads time-to-full-display tracking and sets it to disabled if false`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.TTFD_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isEnableTimeToFullDisplayTracing)
    }

    @Test
    fun `applyMetadata reads enabled integrations to SDK Version`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.SENTRY_GRADLE_PLUGIN_INTEGRATIONS to "Database Instrumentation,OkHttp Instrumentation")
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        val resultingSet = fixture.options.sdkVersion?.integrationSet
        assertNotNull(resultingSet)
        assert(resultingSet.containsAll(listOf("Database Instrumentation", "OkHttp Instrumentation")))
    }

    @Test
    fun `applyMetadata reads enable root checker to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.ENABLE_ROOT_CHECK to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isEnableRootCheck)
    }

    @Test
    fun `applyMetadata reads enable root check and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isEnableRootCheck)
    }

    @Test
    fun `applyMetadata reads enabled flag to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.ENABLE_SENTRY to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isEnabled)
    }

    @Test
    fun `applyMetadata reads enabled flag to options and keeps default if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isEnabled)
    }

    @Test
    fun `applyMetadata reads sendModules flag to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.SEND_MODULES to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isSendModules)
    }

    @Test
    fun `applyMetadata reads sendModules flag to options and keeps default if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isSendModules)
    }

    @Test
    fun `applyMetadata reads performance-v2 flag to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.ENABLE_PERFORMANCE_V2 to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isEnablePerformanceV2)
    }

    @Test
    fun `applyMetadata reads performance-v2 flag to options and keeps default if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isEnablePerformanceV2)
    }

    @Test
    fun `applyMetadata reads startupProfiling flag to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.ENABLE_APP_START_PROFILING to true)
        val context = fixture.getContext(metaData = bundle)
        fixture.options.profilesSampleRate = 1.0

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isEnableAppStartProfiling)
    }

    @Test
    fun `applyMetadata reads startupProfiling flag to options and keeps default if not found`() {
        // Arrange
        val context = fixture.getContext()
        fixture.options.profilesSampleRate = 1.0

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isEnableAppStartProfiling)
    }

    @Test
    fun `applyMetadata reads enableScopePersistence flag to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.ENABLE_SCOPE_PERSISTENCE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isEnableScopePersistence)
    }

    @Test
    fun `applyMetadata reads enableScopePersistence flag to options and keeps default if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isEnableScopePersistence)
    }

    @Test
    fun `applyMetadata does not override replays onErrorSampleRate from options`() {
        // Arrange
        val expectedSampleRate = 0.99f
        fixture.options.experimental.sessionReplay.onErrorSampleRate = expectedSampleRate.toDouble()
        val bundle = bundleOf(ManifestMetadataReader.REPLAYS_ERROR_SAMPLE_RATE to 0.1f)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(expectedSampleRate.toDouble(), fixture.options.experimental.sessionReplay.onErrorSampleRate)
    }

    @Test
    fun `applyMetadata reads forceInit flag to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.FORCE_INIT to true)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.isForceInit)
    }

    @Test
    fun `applyMetadata reads forceInit flag to options and keeps default if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertFalse(fixture.options.isForceInit)
    }

    @Test
    fun `applyMetadata reads replays onErrorSampleRate from metadata`() {
        // Arrange
        val expectedSampleRate = 0.99f

        val bundle = bundleOf(ManifestMetadataReader.REPLAYS_ERROR_SAMPLE_RATE to expectedSampleRate)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(expectedSampleRate.toDouble(), fixture.options.experimental.sessionReplay.onErrorSampleRate)
    }

    @Test
    fun `applyMetadata without specifying replays onErrorSampleRate, stays null`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertNull(fixture.options.experimental.sessionReplay.onErrorSampleRate)
    }

    @Test
    fun `applyMetadata reads session replay mask flags to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.REPLAYS_MASK_ALL_TEXT to false, ManifestMetadataReader.REPLAYS_MASK_ALL_IMAGES to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.experimental.sessionReplay.unmaskViewClasses.contains(SentryReplayOptions.IMAGE_VIEW_CLASS_NAME))
        assertTrue(fixture.options.experimental.sessionReplay.unmaskViewClasses.contains(SentryReplayOptions.TEXT_VIEW_CLASS_NAME))
    }

    @Test
    fun `applyMetadata reads session replay mask flags to options and keeps default if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertTrue(fixture.options.experimental.sessionReplay.maskViewClasses.contains(SentryReplayOptions.IMAGE_VIEW_CLASS_NAME))
        assertTrue(fixture.options.experimental.sessionReplay.maskViewClasses.contains(SentryReplayOptions.TEXT_VIEW_CLASS_NAME))
    }

    @Test
    fun `applyMetadata reads integers even when expecting floats`() {
        // Arrange
        val expectedSampleRate: Int = 1

        val bundle = bundleOf(
            ManifestMetadataReader.SAMPLE_RATE to expectedSampleRate,
            ManifestMetadataReader.TRACES_SAMPLE_RATE to expectedSampleRate,
            ManifestMetadataReader.PROFILES_SAMPLE_RATE to expectedSampleRate,
            ManifestMetadataReader.REPLAYS_SESSION_SAMPLE_RATE to expectedSampleRate,
            ManifestMetadataReader.REPLAYS_ERROR_SAMPLE_RATE to expectedSampleRate
        )
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(expectedSampleRate.toDouble(), fixture.options.sampleRate)
        assertEquals(expectedSampleRate.toDouble(), fixture.options.tracesSampleRate)
        assertEquals(expectedSampleRate.toDouble(), fixture.options.profilesSampleRate)
        assertEquals(expectedSampleRate.toDouble(), fixture.options.experimental.sessionReplay.sessionSampleRate)
        assertEquals(expectedSampleRate.toDouble(), fixture.options.experimental.sessionReplay.onErrorSampleRate)
    }

    @Test
    fun `applyMetadata reads maxBreadcrumbs to options and sets the value if found`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.MAX_BREADCRUMBS to 1)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(1, fixture.options.maxBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads maxBreadcrumbs to options and keeps default if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options, fixture.buildInfoProvider)

        // Assert
        assertEquals(100, fixture.options.maxBreadcrumbs)
    }
}
