package io.sentry.android.core

import android.content.Context
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import io.sentry.ILogger
import io.sentry.SentryLevel
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ManifestMetadataReaderTest {

    private class Fixture {
        val logger = mock<ILogger>()
        val options = SentryAndroidOptions().apply {
            setLogger(logger)
        }

        fun getContext(metaData: Bundle = Bundle()): Context {
            return ContextUtilsTest.mockMetaData(metaData = metaData)
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

        ManifestMetadataReader.applyMetadata(context, fixture.options)

        verify(fixture.logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `applyMetadata reads sampleRate from metadata`() {
        // Arrange
        val expectedSampleRate = 0.99f

        val bundle = bundleOf(ManifestMetadataReader.SAMPLE_RATE to expectedSampleRate)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

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
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertEquals(expectedSampleRate.toDouble(), fixture.options.sampleRate)
    }

    @Test
    fun `applyMetadata without specifying sampleRate, stays null`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertNull(fixture.options.sampleRate)
    }

    @Test
    fun `applyMetadata reads session tracking to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.SESSION_TRACKING_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertFalse(fixture.options.isEnableAutoSessionTracking)
    }

    @Test
    fun `applyMetadata reads session tracking and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertTrue(fixture.options.isEnableAutoSessionTracking)
    }

    @Test
    fun `applyMetadata reads auto session tracking to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.AUTO_SESSION_TRACKING_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertFalse(fixture.options.isEnableAutoSessionTracking)
    }

    @Test
    fun `applyMetadata reads environment to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.ENVIRONMENT to "env")
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertEquals("env", fixture.options.environment)
    }

    @Test
    fun `applyMetadata reads environment and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertNull(fixture.options.environment)
    }

    @Test
    fun `applyMetadata reads release to options`() {
        // Arrange

        val bundle = bundleOf(ManifestMetadataReader.RELEASE to "release")
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertEquals("release", fixture.options.release)
    }

    @Test
    fun `applyMetadata reads release and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertNull(fixture.options.release)
    }

    @Test
    fun `applyMetadata reads session tracking interval to options`() {
        // Arrange

        val bundle = bundleOf(ManifestMetadataReader.SESSION_TRACKING_TIMEOUT_INTERVAL_MILLIS to 1000)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertEquals(1000.toLong(), fixture.options.sessionTrackingIntervalMillis)
    }

    @Test
    fun `applyMetadata reads session tracking interval and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertEquals(30000.toLong(), fixture.options.sessionTrackingIntervalMillis)
    }

    @Test
    fun `applyMetadata reads anr interval to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.ANR_TIMEOUT_INTERVAL_MILLIS to 1000)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertEquals(1000.toLong(), fixture.options.anrTimeoutIntervalMillis)
    }

    @Test
    fun `applyMetadata reads anr interval to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertEquals(5000.toLong(), fixture.options.anrTimeoutIntervalMillis)
    }

    @Test
    fun `applyMetadata reads activity breadcrumbs to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.BREADCRUMBS_ACTIVITY_LIFECYCLE_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertFalse(fixture.options.isEnableActivityLifecycleBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads activity breadcrumbs and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertTrue(fixture.options.isEnableActivityLifecycleBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads app lifecycle breadcrumbs to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.BREADCRUMBS_APP_LIFECYCLE_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertFalse(fixture.options.isEnableAppLifecycleBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads app lifecycle breadcrumbs and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertTrue(fixture.options.isEnableAppLifecycleBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads system events breadcrumbs to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.BREADCRUMBS_SYSTEM_EVENTS_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertFalse(fixture.options.isEnableSystemEventBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads system events breadcrumbs and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertTrue(fixture.options.isEnableSystemEventBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads app components breadcrumbs to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.BREADCRUMBS_APP_COMPONENTS_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertFalse(fixture.options.isEnableAppComponentBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads app components breadcrumbs and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertTrue(fixture.options.isEnableAppComponentBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads enableUncaughtExceptionHandler to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.UNCAUGHT_EXCEPTION_HANDLER_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertFalse(fixture.options.isEnableUncaughtExceptionHandler)
    }

    @Test
    fun `applyMetadata reads enableUncaughtExceptionHandler and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertTrue(fixture.options.isEnableUncaughtExceptionHandler)
    }

    @Test
    fun `applyMetadata reads attachThreads to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.ATTACH_THREADS to true)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertTrue(fixture.options.isAttachThreads)
    }

    @Test
    fun `applyMetadata reads attachThreads and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertFalse(fixture.options.isAttachThreads)
    }

    @Test
    fun `applyMetadata reads isDebug to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.DEBUG to true)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertTrue(fixture.options.isDebug)
    }

    @Test
    fun `applyMetadata reads isDebug and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

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
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertEquals(SentryLevel.INFO, fixture.options.diagnosticLevel)
    }

    @Test
    fun `applyMetadata reads diagnosticLevel to options and keeps default`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.DEBUG to true)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertEquals(SentryLevel.DEBUG, fixture.options.diagnosticLevel)
    }

    @Test
    fun `applyMetadata reads anrEnabled to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.ANR_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertFalse(fixture.options.isAnrEnabled)
    }

    @Test
    fun `applyMetadata reads anrEnabled to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertTrue(fixture.options.isAnrEnabled)
    }

    @Test
    fun `applyMetadata reads anrReportInDebug to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.ANR_REPORT_DEBUG to true)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertTrue(fixture.options.isAnrReportInDebug)
    }

    @Test
    fun `applyMetadata reads anrReportInDebug to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertFalse(fixture.options.isAnrReportInDebug)
    }

    @Test
    fun `applyMetadata reads DSN to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.DSN to "dsn")
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertEquals("dsn", fixture.options.dsn)
    }

    @Test
    fun `applyMetadata reads DSN to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()
        fixture.options.dsn = "myOwnDsn"

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertEquals("myOwnDsn", fixture.options.dsn)
    }

    @Test
    fun `applyMetadata reads enableNdk to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.NDK_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertFalse(fixture.options.isEnableNdk)
    }

    @Test
    fun `applyMetadata reads enableNdk to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertTrue(fixture.options.isEnableNdk)
    }

    @Test
    fun `applyMetadata reads enableScopeSync to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.NDK_SCOPE_SYNC_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertFalse(fixture.options.isEnableScopeSync)
    }

    @Test
    fun `applyMetadata reads enableScopeSync to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

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
        ManifestMetadataReader.applyMetadata(context, fixture.options)

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
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertEquals(expectedSampleRate.toDouble(), fixture.options.tracesSampleRate)
    }

    @Test
    fun `applyMetadata without specifying tracesSampleRate, stays null`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertNull(fixture.options.tracesSampleRate)
    }

    @Test
    fun `applyMetadata reads enableAutoActivityLifecycleTracing to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.TRACES_ACTIVITY_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertFalse(fixture.options.isEnableAutoActivityLifecycleTracing)
    }

    @Test
    fun `applyMetadata reads enableAutoActivityLifecycleTracing to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertTrue(fixture.options.isEnableAutoActivityLifecycleTracing)
    }

    @Test
    fun `applyMetadata reads enableActivityLifecycleTracingAutoFinish to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.TRACES_ACTIVITY_AUTO_FINISH_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertFalse(fixture.options.isEnableActivityLifecycleTracingAutoFinish)
    }

    @Test
    fun `applyMetadata reads enableActivityLifecycleTracingAutoFinish to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertTrue(fixture.options.isEnableActivityLifecycleTracingAutoFinish)
    }

    @Test
    fun `applyMetadata reads traceSampling to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.TRACE_SAMPLING to true)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertTrue(fixture.options.isTraceSampling)
    }

    @Test
    fun `applyMetadata reads traceSampling to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertFalse(fixture.options.isTraceSampling)
    }

    @Test
    fun `applyMetadata reads enableTracesProfiling to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.TRACES_PROFILING_ENABLE to true)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertTrue(fixture.options.isProfilingEnabled)
    }

    @Test
    fun `applyMetadata reads enableTracesProfiling to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertFalse(fixture.options.isProfilingEnabled)
    }

    @Test
    fun `applyMetadata reads tracingOrigins to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.TRACING_ORIGINS to """localhost,^(http|https)://api\..*$""")
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertEquals(listOf("localhost", """^(http|https)://api\..*$"""), fixture.options.tracingOrigins)
    }

    @Test
    fun `applyMetadata reads tracingOrigins to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertTrue(fixture.options.tracingOrigins.isEmpty())
    }

    @Test
    fun `applyMetadata reads proguardUuid to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.PROGUARD_UUID to "proguard-id")
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertEquals("proguard-id", fixture.options.proguardUuid)
    }

    @Test
    fun `applyMetadata reads proguardUuid to options and keeps default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertNull(fixture.options.proguardUuid)
    }

    @Test
    fun `applyMetadata reads ui events breadcrumbs to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.BREADCRUMBS_USER_INTERACTION_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertFalse(fixture.options.isEnableUserInteractionBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads ui events breadcrumbs and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertTrue(fixture.options.isEnableUserInteractionBreadcrumbs)
    }

    @Test
    fun `applyMetadata reads attach screenshots to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.ATTACH_SCREENSHOT to true)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertTrue(fixture.options.isAttachScreenshot)
    }

    @Test
    fun `applyMetadata reads attach screenshots and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertFalse(fixture.options.isAttachScreenshot)
    }

    @Test
    fun `applyMetadata reads send client reports to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.CLIENT_REPORTS_ENABLE to false)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertFalse(fixture.options.isSendClientReports)
    }

    @Test
    fun `applyMetadata reads send client reports and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertTrue(fixture.options.isSendClientReports)
    }

    @Test
    fun `applyMetadata reads user interaction tracing to options`() {
        // Arrange
        val bundle = bundleOf(ManifestMetadataReader.TRACES_UI_ENABLE to true)
        val context = fixture.getContext(metaData = bundle)

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertTrue(fixture.options.isEnableUserInteractionTracing)
    }

    @Test
    fun `applyMetadata reads user interaction tracing and keep default value if not found`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

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
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertEquals(expectedIdleTimeout.toLong(), fixture.options.idleTimeout)
    }

    @Test
    fun `applyMetadata without specifying idleTimeout, stays default`() {
        // Arrange
        val context = fixture.getContext()

        // Act
        ManifestMetadataReader.applyMetadata(context, fixture.options)

        // Assert
        assertEquals(3000L, fixture.options.idleTimeout)
    }
}
