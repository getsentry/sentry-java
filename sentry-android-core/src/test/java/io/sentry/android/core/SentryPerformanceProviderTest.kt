package io.sentry.android.core

import android.app.Application
import android.content.pm.ProviderInfo
import android.os.Build
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ILogger
import io.sentry.JsonSerializer
import io.sentry.Sentry
import io.sentry.SentryAppStartProfilingOptions
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.performance.AppStartMetrics
import io.sentry.android.core.performance.AppStartMetrics.AppStartType
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [Build.VERSION_CODES.N],
    shadows = [SentryShadowProcess::class]
)
class SentryPerformanceProviderTest {

    private lateinit var cache: File
    private lateinit var sentryCache: File
    private lateinit var traceDir: File

    private inner class Fixture {
        val mockContext = mock<Application>()
        val providerInfo = ProviderInfo()
        val logger = mock<ILogger>()
        lateinit var configFile: File

        fun getSut(sdkVersion: Int = Build.VERSION_CODES.S, authority: String = AUTHORITY, handleFile: ((config: File) -> Unit)? = null): SentryPerformanceProvider {
            val buildInfoProvider: BuildInfoProvider = mock()
            whenever(buildInfoProvider.sdkInfoVersion).thenReturn(sdkVersion)
            whenever(mockContext.cacheDir).thenReturn(cache)
            whenever(mockContext.applicationContext).thenReturn(mockContext)
            configFile = File(sentryCache, Sentry.APP_START_PROFILING_CONFIG_FILE_NAME)
            handleFile?.invoke(configFile)

            providerInfo.authority = authority
            return SentryPerformanceProvider(logger, buildInfoProvider).apply {
                attachInfo(mockContext, providerInfo)
            }
        }
    }

    private val fixture = Fixture()

    @BeforeTest
    fun `set up`() {
        AppStartMetrics.getInstance().clear()
        SentryShadowProcess.setStartUptimeMillis(1234)
        cache = Files.createTempDirectory("sentry-disk-cache-test").toAbsolutePath().toFile()
        sentryCache = File(cache, "sentry")
        traceDir = File(sentryCache, "traces")
        cache.mkdir()
        sentryCache.mkdir()
        traceDir.mkdir()
    }

    @AfterTest
    fun cleanup() {
        AppStartMetrics.getInstance().clear()
        cache.deleteRecursively()
        Sentry.close()
    }

    @Test
    fun `when missing applicationId, SentryPerformanceProvider throws`() {
        assertFailsWith<IllegalStateException> {
            fixture.getSut(authority = SentryPerformanceProvider::class.java.name)
        }
    }

    @Test
    fun `provider starts appStartTimeSpan`() {
        assertTrue(AppStartMetrics.getInstance().sdkInitTimeSpan.hasNotStarted())
        assertTrue(AppStartMetrics.getInstance().appStartTimeSpan.hasNotStarted())
        fixture.getSut()
        assertTrue(AppStartMetrics.getInstance().sdkInitTimeSpan.hasStarted())
        assertTrue(AppStartMetrics.getInstance().appStartTimeSpan.hasStarted())
    }

    @Test
    fun `provider sets cold start based on first activity`() {
        val provider = fixture.getSut()

        // up until this point app start is not known
        assertEquals(AppStartType.UNKNOWN, AppStartMetrics.getInstance().appStartType)

        // when there's no saved state
        provider.activityCallback!!.onActivityCreated(mock(), null)
        // then app start should be cold
        assertEquals(AppStartType.COLD, AppStartMetrics.getInstance().appStartType)
    }

    @Test
    fun `provider sets warm start based on first activity`() {
        val provider = fixture.getSut()

        // up until this point app start is not known
        assertEquals(AppStartType.UNKNOWN, AppStartMetrics.getInstance().appStartType)

        // when there's a saved state
        provider.activityCallback!!.onActivityCreated(mock(), Bundle())

        // then app start should be warm
        assertEquals(AppStartType.WARM, AppStartMetrics.getInstance().appStartType)
    }

    @Test
    fun `provider keeps startup state even if multiple activities are launched`() {
        val provider = fixture.getSut()

        // when there's a saved state
        provider.activityCallback!!.onActivityCreated(mock(), Bundle())

        // then app start should be warm
        assertEquals(AppStartType.WARM, AppStartMetrics.getInstance().appStartType)

        // when another activity is launched cold
        provider.activityCallback!!.onActivityCreated(mock(), null)

        // then app start should remain warm
        assertEquals(AppStartType.WARM, AppStartMetrics.getInstance().appStartType)
    }

    @Test
    fun `provider sets both appstart and sdk init start + end times`() {
        val provider = fixture.getSut()
        provider.onAppStartDone()

        val metrics = AppStartMetrics.getInstance()
        assertTrue(metrics.appStartTimeSpan.hasStarted())
        assertTrue(metrics.appStartTimeSpan.hasStopped())

        assertTrue(metrics.sdkInitTimeSpan.hasStarted())
        assertTrue(metrics.sdkInitTimeSpan.hasStopped())
    }

    @Test
    fun `provider properly registers and unregisters ActivityLifecycleCallbacks`() {
        val provider = fixture.getSut()

        // It register once for the provider itself and once for the appStartMetrics
        verify(fixture.mockContext, times(2)).registerActivityLifecycleCallbacks(any())
        provider.onAppStartDone()
        verify(fixture.mockContext).unregisterActivityLifecycleCallbacks(any())
    }

    //region app start profiling
    @Test
    fun `when config file does not exists, nothing happens`() {
        fixture.getSut()
        assertNull(AppStartMetrics.getInstance().appStartProfiler)
        assertNull(AppStartMetrics.getInstance().appStartContinuousProfiler)
        verify(fixture.logger, never()).log(any(), any())
    }

    @Test
    fun `when config file is not readable, nothing happens`() {
        fixture.getSut { config ->
            writeConfig(config)
            config.setReadable(false)
        }
        assertNull(AppStartMetrics.getInstance().appStartProfiler)
        assertNull(AppStartMetrics.getInstance().appStartContinuousProfiler)
        verify(fixture.logger, never()).log(any(), any())
    }

    @Test
    fun `when config file is empty, profiler is not started`() {
        fixture.getSut { config ->
            config.createNewFile()
        }
        assertNull(AppStartMetrics.getInstance().appStartProfiler)
        assertNull(AppStartMetrics.getInstance().appStartContinuousProfiler)
        verify(fixture.logger).log(
            eq(SentryLevel.WARNING),
            eq("Unable to deserialize the SentryAppStartProfilingOptions. App start profiling will not start.")
        )
    }

    @Test
    fun `when profiling is disabled, profiler is not started`() {
        fixture.getSut { config ->
            writeConfig(config, profilingEnabled = false, continuousProfilingEnabled = false)
        }
        assertNull(AppStartMetrics.getInstance().appStartProfiler)
        verify(fixture.logger).log(
            eq(SentryLevel.INFO),
            eq("Profiling is not enabled. App start profiling will not start.")
        )
    }

    @Test
    fun `when continuous profiling is disabled, continuous profiler is not started`() {
        fixture.getSut { config ->
            writeConfig(config, continuousProfilingEnabled = false, profilingEnabled = false)
        }
        assertNull(AppStartMetrics.getInstance().appStartContinuousProfiler)
        verify(fixture.logger).log(
            eq(SentryLevel.INFO),
            eq("Profiling is not enabled. App start profiling will not start.")
        )
    }

    @Test
    fun `when trace is not sampled, profiler is not started and sample decision is stored`() {
        fixture.getSut { config ->
            writeConfig(config, continuousProfilingEnabled = false, traceSampled = false, profileSampled = true)
        }
        assertNull(AppStartMetrics.getInstance().appStartProfiler)
        assertNotNull(AppStartMetrics.getInstance().appStartSamplingDecision)
        assertFalse(AppStartMetrics.getInstance().appStartSamplingDecision!!.sampled)
        // If trace is not sampled, profile is not sample, either
        assertFalse(AppStartMetrics.getInstance().appStartSamplingDecision!!.profileSampled)
        verify(fixture.logger).log(
            eq(SentryLevel.DEBUG),
            eq("App start profiling was not sampled. It will not start.")
        )
    }

    @Test
    fun `when profile is not sampled, profiler is not started and sample decision is stored`() {
        fixture.getSut { config ->
            writeConfig(config, continuousProfilingEnabled = false, traceSampled = true, profileSampled = false)
        }
        assertNull(AppStartMetrics.getInstance().appStartProfiler)
        assertNotNull(AppStartMetrics.getInstance().appStartSamplingDecision)
        assertTrue(AppStartMetrics.getInstance().appStartSamplingDecision!!.sampled)
        assertFalse(AppStartMetrics.getInstance().appStartSamplingDecision!!.profileSampled)
        verify(fixture.logger).log(
            eq(SentryLevel.DEBUG),
            eq("App start profiling was not sampled. It will not start.")
        )
    }

    // This case should never happen in reality, but it's technically possible to have such configuration
    @Test
    fun `when both transaction and continuous profilers are enabled, only continuous profiler is created`() {
        fixture.getSut { config ->
            writeConfig(config)
        }
        assertNull(AppStartMetrics.getInstance().appStartProfiler)
        assertNotNull(AppStartMetrics.getInstance().appStartContinuousProfiler)
        assertTrue(AppStartMetrics.getInstance().appStartContinuousProfiler!!.isRunning)
        verify(fixture.logger).log(
            eq(SentryLevel.DEBUG),
            eq("App start continuous profiling started.")
        )
    }

    @Test
    fun `when profiler starts, it is set in AppStartMetrics`() {
        fixture.getSut { config ->
            writeConfig(config, continuousProfilingEnabled = false)
        }
        assertNotNull(AppStartMetrics.getInstance().appStartProfiler)
        assertNotNull(AppStartMetrics.getInstance().appStartSamplingDecision)
        assertTrue(AppStartMetrics.getInstance().appStartProfiler!!.isRunning)
        assertTrue(AppStartMetrics.getInstance().appStartSamplingDecision!!.sampled)
        assertTrue(AppStartMetrics.getInstance().appStartSamplingDecision!!.profileSampled)
        verify(fixture.logger).log(
            eq(SentryLevel.DEBUG),
            eq("App start profiling started.")
        )
    }

    @Test
    fun `when continuous profiler starts, it is set in AppStartMetrics`() {
        fixture.getSut { config ->
            writeConfig(config, profilingEnabled = false)
        }
        assertNotNull(AppStartMetrics.getInstance().appStartContinuousProfiler)
        assertTrue(AppStartMetrics.getInstance().appStartContinuousProfiler!!.isRunning)
        verify(fixture.logger).log(
            eq(SentryLevel.DEBUG),
            eq("App start continuous profiling started.")
        )
    }

    @Test
    fun `when provider is closed, profiler is stopped`() {
        val provider = fixture.getSut { config ->
            writeConfig(config, continuousProfilingEnabled = false)
        }
        provider.shutdown()
        assertNotNull(AppStartMetrics.getInstance().appStartProfiler)
        assertFalse(AppStartMetrics.getInstance().appStartProfiler!!.isRunning)
    }

    @Test
    fun `when provider is closed, continuous profiler is stopped`() {
        val provider = fixture.getSut { config ->
            writeConfig(config, profilingEnabled = false)
        }
        provider.shutdown()
        assertNotNull(AppStartMetrics.getInstance().appStartContinuousProfiler)
        assertFalse(AppStartMetrics.getInstance().appStartContinuousProfiler!!.isRunning)
    }

    private fun writeConfig(
        configFile: File,
        profilingEnabled: Boolean = true,
        continuousProfilingEnabled: Boolean = true,
        traceSampled: Boolean = true,
        traceSampleRate: Double = 1.0,
        profileSampled: Boolean = true,
        profileSampleRate: Double = 1.0,
        profilingTracesDirPath: String = traceDir.absolutePath
    ) {
        val appStartProfilingOptions = SentryAppStartProfilingOptions()
        appStartProfilingOptions.isProfilingEnabled = profilingEnabled
        appStartProfilingOptions.isContinuousProfilingEnabled = continuousProfilingEnabled
        appStartProfilingOptions.isTraceSampled = traceSampled
        appStartProfilingOptions.traceSampleRate = traceSampleRate
        appStartProfilingOptions.isProfileSampled = profileSampled
        appStartProfilingOptions.profileSampleRate = profileSampleRate
        appStartProfilingOptions.profilingTracesDirPath = profilingTracesDirPath
        appStartProfilingOptions.profilingTracesHz = 101
        JsonSerializer(SentryOptions.empty()).serialize(appStartProfilingOptions, FileWriter(configFile))
    }
    //endregion

    companion object {
        private const val AUTHORITY = "io.sentry.sample.SentryPerformanceProvider"
    }
}
