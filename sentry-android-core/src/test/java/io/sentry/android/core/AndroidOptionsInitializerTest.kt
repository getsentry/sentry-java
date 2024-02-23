package io.sentry.android.core

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.DefaultTransactionPerformanceCollector
import io.sentry.ILogger
import io.sentry.MainEventProcessor
import io.sentry.SentryOptions
import io.sentry.android.core.cache.AndroidEnvelopeCache
import io.sentry.android.core.internal.gestures.AndroidViewGestureTargetLocator
import io.sentry.android.core.internal.modules.AssetsModulesLoader
import io.sentry.android.core.internal.util.AndroidMainThreadChecker
import io.sentry.android.fragment.FragmentLifecycleIntegration
import io.sentry.android.timber.SentryTimberIntegration
import io.sentry.cache.PersistingOptionsObserver
import io.sentry.cache.PersistingScopeObserver
import io.sentry.compose.gestures.ComposeGestureTargetLocator
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidOptionsInitializerTest {

    class Fixture(val context: Context, private val file: File) {
        val sentryOptions = SentryAndroidOptions()
        lateinit var mockContext: Context
        val logger = mock<ILogger>()

        fun initSut(
            metadata: Bundle? = null,
            hasAppContext: Boolean = true,
            useRealContext: Boolean = false,
            configureOptions: SentryAndroidOptions.() -> Unit = {},
            configureContext: Context.() -> Unit = {},
            assets: AssetManager? = null
        ) {
            mockContext = if (metadata != null) {
                ContextUtilsTestHelper.mockMetaData(
                    mockContext = ContextUtilsTestHelper.createMockContext(hasAppContext),
                    metaData = metadata,
                    assets = assets
                )
            } else {
                ContextUtilsTestHelper.createMockContext(hasAppContext)
            }
            whenever(mockContext.cacheDir).thenReturn(file)
            if (mockContext.applicationContext != null) {
                whenever(mockContext.applicationContext.cacheDir).thenReturn(file)
            }
            mockContext.configureContext()
            AndroidOptionsInitializer.loadDefaultAndMetadataOptions(
                sentryOptions,
                if (useRealContext) context else mockContext
            )

            val loadClass = LoadClass()
            val activityFramesTracker = ActivityFramesTracker(loadClass, sentryOptions)

            AndroidOptionsInitializer.installDefaultIntegrations(
                if (useRealContext) context else mockContext,
                sentryOptions,
                BuildInfoProvider(AndroidLogger()),
                loadClass,
                activityFramesTracker,
                false,
                false
            )

            sentryOptions.configureOptions()
            AndroidOptionsInitializer.initializeIntegrationsAndProcessors(
                sentryOptions,
                if (useRealContext) context else mockContext,
                loadClass,
                activityFramesTracker
            )
        }

        fun initSutWithClassLoader(
            minApi: Int = Build.VERSION_CODES.KITKAT,
            classesToLoad: List<String> = emptyList(),
            isFragmentAvailable: Boolean = false,
            isTimberAvailable: Boolean = false
        ) {
            mockContext = ContextUtilsTestHelper.mockMetaData(
                mockContext = ContextUtilsTestHelper.createMockContext(hasAppContext = true),
                metaData = Bundle().apply {
                    putString(ManifestMetadataReader.DSN, "https://key@sentry.io/123")
                }
            )
            sentryOptions.isDebug = true
            val buildInfo = createBuildInfo(minApi)
            val loadClass = createClassMock(classesToLoad)
            val activityFramesTracker = ActivityFramesTracker(loadClass, sentryOptions)

            AndroidOptionsInitializer.loadDefaultAndMetadataOptions(
                sentryOptions,
                context,
                logger,
                buildInfo
            )

            AndroidOptionsInitializer.installDefaultIntegrations(
                context,
                sentryOptions,
                buildInfo,
                loadClass,
                activityFramesTracker,
                isFragmentAvailable,
                isTimberAvailable
            )

            AndroidOptionsInitializer.initializeIntegrationsAndProcessors(
                sentryOptions,
                context,
                buildInfo,
                loadClass,
                activityFramesTracker
            )
        }

        private fun createBuildInfo(minApi: Int): BuildInfoProvider {
            val buildInfo = mock<BuildInfoProvider>()
            whenever(buildInfo.sdkInfoVersion).thenReturn(minApi)
            return buildInfo
        }

        private fun createClassMock(classes: List<String>): LoadClass {
            val loadClassMock = mock<LoadClass>()
            classes.forEach {
                whenever(loadClassMock.loadClass(eq(it), any()))
                    .thenReturn(Class.forName(it, false, this::class.java.classLoader))
                whenever(loadClassMock.isClassAvailable(eq(it), any<SentryOptions>()))
                    .thenReturn(true)
            }
            return loadClassMock
        }
    }

    private lateinit var fixture: Fixture

    @BeforeTest
    fun `set up`() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        fixture = Fixture(appContext, appContext.cacheDir)
    }

    @Test
    fun `logger set to AndroidLogger`() {
        fixture.initSut()
        val logger = SentryOptions::class.java.declaredFields.first { it.name == "logger" }
        logger.isAccessible = true
        val loggerField = logger.get(fixture.sentryOptions)
        val innerLogger = loggerField.javaClass.declaredFields.first { it.name == "logger" }
        innerLogger.isAccessible = true
        assertTrue(innerLogger.get(loggerField) is AndroidLogger)
    }

    @Test
    fun `flush timeout is set to Android specific default value`() {
        fixture.initSut()
        assertEquals(AndroidOptionsInitializer.DEFAULT_FLUSH_TIMEOUT_MS, fixture.sentryOptions.flushTimeoutMillis)
    }

    @Test
    fun `flush timeout can be overridden`() {
        fixture.initSut(configureOptions = {
            flushTimeoutMillis = 1234
        })
        assertEquals(1234, fixture.sentryOptions.flushTimeoutMillis)
    }

    @Test
    fun `AndroidEventProcessor added to processors list`() {
        fixture.initSut()
        val actual =
            fixture.sentryOptions.eventProcessors.firstOrNull { it is DefaultAndroidEventProcessor }
        assertNotNull(actual)
    }

    @Test
    fun `PerformanceAndroidEventProcessor added to processors list`() {
        fixture.initSut()
        val actual =
            fixture.sentryOptions.eventProcessors.firstOrNull { it is PerformanceAndroidEventProcessor }
        assertNotNull(actual)
    }

    @Test
    fun `MainEventProcessor added to processors list and its the 1st`() {
        fixture.initSut()
        val actual = fixture.sentryOptions.eventProcessors.firstOrNull { it is MainEventProcessor }
        assertNotNull(actual)
    }

    @Test
    fun `ScreenshotEventProcessor added to processors list`() {
        fixture.initSut()
        val actual =
            fixture.sentryOptions.eventProcessors.firstOrNull { it is ScreenshotEventProcessor }
        assertNotNull(actual)
    }

    @Test
    fun `ViewHierarchyEventProcessor added to processors list`() {
        fixture.initSut()
        val actual =
            fixture.sentryOptions.eventProcessors.firstOrNull { it is ViewHierarchyEventProcessor }
        assertNotNull(actual)
    }

    @Test
    fun `AnrV2EventProcessor added to processors list`() {
        fixture.initSut()
        val actual =
            fixture.sentryOptions.eventProcessors.firstOrNull { it is AnrV2EventProcessor }
        assertNotNull(actual)
    }

    @Test
    fun `envelopesDir should be set at initialization`() {
        fixture.initSut()

        assertTrue(
            fixture.sentryOptions.cacheDirPath?.endsWith(
                "${File.separator}cache${File.separator}sentry"
            )!!
        )
    }

    @Test
    fun `getCacheDir returns sentry subfolder`() {
        fixture.initSut()
        assertTrue(AndroidOptionsInitializer.getCacheDir(fixture.context).path.endsWith("${File.separator}cache${File.separator}sentry"))
    }

    @Test
    fun `profilingTracesDirPath should be set at initialization`() {
        fixture.initSut()

        assertTrue(
            fixture.sentryOptions.profilingTracesDirPath?.endsWith(
                "${File.separator}cache${File.separator}sentry${File.separator}profiling_traces"
            )!!
        )
        assertFalse(File(fixture.sentryOptions.profilingTracesDirPath!!).exists())
    }

    @Test
    fun `outboxDir should be set at initialization`() {
        fixture.initSut()

        assertTrue(
            fixture.sentryOptions.outboxPath?.endsWith(
                "${File.separator}cache${File.separator}sentry${File.separator}outbox"
            )!!
        )
    }

    @Test
    fun `init should set context package name as appInclude`() {
        fixture.initSut(useRealContext = true)

        // cant mock PackageInfo, its buggy
        assertTrue(fixture.sentryOptions.inAppIncludes.contains("io.sentry.android.core.test"))
    }

    @Test
    fun `init should set release if empty`() {
        fixture.initSut(useRealContext = true)

        // cant mock PackageInfo, its buggy
        assertTrue(fixture.sentryOptions.release!!.startsWith("io.sentry.android.core.test@"))
    }

    @Test
    fun `init should not replace options if set on manifest`() {
        fixture.initSut(configureOptions = { release = "release" })

        assertEquals("release", fixture.sentryOptions.release)
    }

    @Test
    fun `init should not set context package name if it starts with android package`() {
        fixture.initSut(configureContext = {
            whenever(packageName).thenReturn("android.context")
        })

        assertFalse(fixture.sentryOptions.inAppIncludes.contains("android.context"))
    }

    @Test
    fun `init should set distinct id on start`() {
        fixture.initSut()

        assertNotNull(fixture.sentryOptions.distinctId) {
            assertTrue(it.isNotEmpty())
        }

        val installation = File(fixture.context.filesDir, Installation.INSTALLATION)
        installation.deleteOnExit()
    }

    @Test
    fun `init should set proguard uuid id on start`() {
        fixture.initSut(
            Bundle().apply {
                putString(ManifestMetadataReader.PROGUARD_UUID, "proguard-uuid")
            },
            hasAppContext = false
        )

        assertEquals("proguard-uuid", fixture.sentryOptions.proguardUuid)
    }

    @Test
    fun `init should set Android transport gate`() {
        fixture.initSut()

        assertNotNull(fixture.sentryOptions.transportGate)
        assertTrue(fixture.sentryOptions.transportGate is AndroidTransportGate)
    }

    @Test
    fun `init should set Android transaction profiler`() {
        fixture.initSut()

        assertNotNull(fixture.sentryOptions.transactionProfiler)
        assertTrue(fixture.sentryOptions.transactionProfiler is AndroidTransactionProfiler)
    }

    @Test
    fun `NdkIntegration will load SentryNdk class and add to the integration list`() {
        fixture.initSutWithClassLoader(classesToLoad = listOfNotNull(NdkIntegration.SENTRY_NDK_CLASS_NAME))

        val actual = fixture.sentryOptions.integrations.firstOrNull { it is NdkIntegration }
        assertNotNull((actual as NdkIntegration).sentryNdkClass)
    }

    @Test
    fun `NdkIntegration won't be enabled, if class not found`() {
        fixture.initSutWithClassLoader(classesToLoad = emptyList())

        val actual = fixture.sentryOptions.integrations.firstOrNull { it is NdkIntegration }
        assertNull((actual as NdkIntegration).sentryNdkClass)
    }

    @Test
    fun `AnrIntegration added to integration list`() {
        fixture.initSut()

        val actual = fixture.sentryOptions.integrations.firstOrNull { it is AnrIntegration }
        assertNotNull(actual)
    }

    @Test
    fun `AnrIntegration is added after AppLifecycleIntegration`() {
        fixture.initSut()

        val appLifecycleIndex =
            fixture.sentryOptions.integrations.indexOfFirst { it is AppLifecycleIntegration }
        val anrIndex = fixture.sentryOptions.integrations.indexOfFirst { it is AnrIntegration }
        assertTrue { appLifecycleIndex < anrIndex }
    }

    @Test
    fun `EnvelopeFileObserverIntegration added to integration list`() {
        fixture.initSut()

        val actual =
            fixture.sentryOptions.integrations.firstOrNull { it is EnvelopeFileObserverIntegration }
        assertNotNull(actual)
    }

    @Test
    fun `SendCachedEnvelopeIntegration added to integration list`() {
        fixture.initSut()

        val actual =
            fixture.sentryOptions.integrations
                .firstOrNull { it is SendCachedEnvelopeIntegration }
        assertNotNull(actual)
    }

    @Test
    fun `When given Context returns a non null ApplicationContext, uses it`() {
        fixture.initSut()

        assertNotNull(fixture.mockContext)
    }

    @Test
    fun `When given Context returns a null ApplicationContext is null, keep given Context`() {
        fixture.initSut(hasAppContext = false)

        assertNotNull(fixture.mockContext)
    }

    @Test
    fun `When given Context is not an Application class, do not add ActivityLifecycleIntegration`() {
        fixture.initSut(hasAppContext = false)

        val actual = fixture.sentryOptions.integrations
            .firstOrNull { it is ActivityLifecycleIntegration }
        assertNull(actual)
    }

    @Test
    fun `When given Context is not an Application class, do not add ActivityBreadcrumbsIntegration`() {
        fixture.initSut(hasAppContext = false)

        val actual = fixture.sentryOptions.integrations
            .firstOrNull { it is ActivityBreadcrumbsIntegration }
        assertNull(actual)
    }

    @Test
    fun `When given Context is not an Application class, do not add UserInteractionIntegration`() {
        fixture.initSut(hasAppContext = false)

        val actual = fixture.sentryOptions.integrations
            .firstOrNull { it is UserInteractionIntegration }
        assertNull(actual)
    }

    @Test
    fun `FragmentLifecycleIntegration added to the integration list if available on classpath`() {
        fixture.initSutWithClassLoader(isFragmentAvailable = true)

        val actual =
            fixture.sentryOptions.integrations.firstOrNull { it is FragmentLifecycleIntegration }
        assertNotNull(actual)
    }

    @Test
    fun `FragmentLifecycleIntegration won't be enabled, it throws class not found`() {
        fixture.initSutWithClassLoader(isFragmentAvailable = false)

        val actual =
            fixture.sentryOptions.integrations.firstOrNull { it is FragmentLifecycleIntegration }
        assertNull(actual)
    }

    @Test
    fun `SentryTimberIntegration added to the integration list if available on classpath`() {
        fixture.initSutWithClassLoader(isTimberAvailable = true)

        val actual =
            fixture.sentryOptions.integrations.firstOrNull { it is SentryTimberIntegration }
        assertNotNull(actual)
    }

    @Test
    fun `SentryTimberIntegration won't be enabled, it throws class not found`() {
        fixture.initSutWithClassLoader(isTimberAvailable = false)

        val actual =
            fixture.sentryOptions.integrations.firstOrNull { it is SentryTimberIntegration }
        assertNull(actual)
    }

    @Test
    fun `AndroidEnvelopeCache is set to options`() {
        fixture.initSut()

        assertTrue { fixture.sentryOptions.envelopeDiskCache is AndroidEnvelopeCache }
    }

    @Test
    fun `CurrentActivityIntegration is added by default`() {
        fixture.initSut(useRealContext = true)

        val actual =
            fixture.sentryOptions.integrations.firstOrNull { it is CurrentActivityIntegration }
        assertNotNull(actual)
    }

    @Test
    fun `When Activity Frames Tracking is enabled, the Activity Frames Tracker should be available`() {
        fixture.initSut(
            hasAppContext = true,
            useRealContext = true,
            configureOptions = {
                isEnableFramesTracking = true
            }
        )

        val activityLifeCycleIntegration = fixture.sentryOptions.integrations
            .first { it is ActivityLifecycleIntegration }

        assertTrue(
            (activityLifeCycleIntegration as ActivityLifecycleIntegration).activityFramesTracker.isFrameMetricsAggregatorAvailable
        )
    }

    @Test
    fun `When Frames Tracking is disabled, the Activity Frames Tracker should not be available`() {
        fixture.initSut(hasAppContext = true, useRealContext = true, configureOptions = {
            isEnableFramesTracking = false
        })

        val activityLifeCycleIntegration = fixture.sentryOptions.integrations
            .first { it is ActivityLifecycleIntegration }

        assertFalse(
            (activityLifeCycleIntegration as ActivityLifecycleIntegration).activityFramesTracker.isFrameMetricsAggregatorAvailable
        )
    }

    @Test
    fun `When Frames Tracking is initially disabled, but enabled via configureOptions it should be available`() {
        fixture.sentryOptions.isEnableFramesTracking = false
        fixture.initSut(
            hasAppContext = true,
            useRealContext = true,
            configureOptions = {
                isEnableFramesTracking = true
            }
        )

        val activityLifeCycleIntegration = fixture.sentryOptions.integrations
            .first { it is ActivityLifecycleIntegration }

        assertTrue(
            (activityLifeCycleIntegration as ActivityLifecycleIntegration).activityFramesTracker.isFrameMetricsAggregatorAvailable
        )
    }

    @Test
    fun `AssetsModulesLoader is set to options`() {
        fixture.initSut()

        assertTrue { fixture.sentryOptions.modulesLoader is AssetsModulesLoader }
    }

    @Test
    fun `AndroidMainThreadChecker is set to options`() {
        fixture.initSut()

        assertTrue { fixture.sentryOptions.mainThreadChecker is AndroidMainThreadChecker }
    }

    @Test
    fun `does not install ComposeGestureTargetLocator, if sentry-compose is not available`() {
        fixture.initSutWithClassLoader()

        assertTrue { fixture.sentryOptions.gestureTargetLocators.size == 1 }
        assertTrue { fixture.sentryOptions.gestureTargetLocators[0] is AndroidViewGestureTargetLocator }
    }

    @Test
    fun `installs ComposeGestureTargetLocator, if sentry-compose is available`() {
        fixture.initSutWithClassLoader(
            classesToLoad = listOf(
                AndroidOptionsInitializer.COMPOSE_CLASS_NAME,
                AndroidOptionsInitializer.SENTRY_COMPOSE_GESTURE_INTEGRATION_CLASS_NAME
            )
        )

        assertTrue { fixture.sentryOptions.gestureTargetLocators.size == 2 }
        assertTrue { fixture.sentryOptions.gestureTargetLocators[0] is AndroidViewGestureTargetLocator }
        assertTrue { fixture.sentryOptions.gestureTargetLocators[1] is ComposeGestureTargetLocator }
    }

    @Test
    fun `AndroidMemoryCollector is set to options`() {
        fixture.initSut()

        assertTrue { fixture.sentryOptions.performanceCollectors.any { it is AndroidMemoryCollector } }
    }

    @Test
    fun `AndroidCpuCollector is set to options`() {
        fixture.initSut()

        assertTrue { fixture.sentryOptions.performanceCollectors.any { it is AndroidCpuCollector } }
    }

    @Test
    fun `DefaultTransactionPerformanceCollector is set to options`() {
        fixture.initSut()

        assertIs<DefaultTransactionPerformanceCollector>(fixture.sentryOptions.transactionPerformanceCollector)
    }

    @Test
    fun `PersistingScopeObserver is set to options`() {
        fixture.initSut()

        assertTrue { fixture.sentryOptions.scopeObservers.any { it is PersistingScopeObserver } }
    }

    @Test
    fun `PersistingOptionsObserver is set to options`() {
        fixture.initSut()

        assertTrue { fixture.sentryOptions.optionsObservers.any { it is PersistingOptionsObserver } }
    }

    @Test
    fun `when cacheDir is not set, persisting observers are not set to options`() {
        fixture.initSut(configureOptions = { cacheDirPath = null })

        assertFalse(fixture.sentryOptions.optionsObservers.any { it is PersistingOptionsObserver })
        assertFalse(fixture.sentryOptions.scopeObservers.any { it is PersistingScopeObserver })
    }

    @Test
    fun `installDefaultIntegrations does not evaluate cacheDir or outboxPath when called`() {
        val mockOptions = spy(fixture.sentryOptions)
        AndroidOptionsInitializer.installDefaultIntegrations(
            fixture.context,
            mockOptions,
            mock(),
            mock(),
            mock(),
            false,
            false
        )
        verify(mockOptions, never()).outboxPath
        verify(mockOptions, never()).cacheDirPath
    }

    @Config(sdk = [30])
    @Test
    fun `AnrV2Integration added to integrations list for API 30 and above`() {
        fixture.initSut(useRealContext = true)

        val anrv2Integration =
            fixture.sentryOptions.integrations.firstOrNull { it is AnrV2Integration }
        assertNotNull(anrv2Integration)

        val anrv1Integration =
            fixture.sentryOptions.integrations.firstOrNull { it is AnrIntegration }
        assertNull(anrv1Integration)
    }

    @Test
    fun `PersistingScopeObserver is not set to options, if scope persistence is disabled`() {
        fixture.initSut(configureOptions = { isEnableScopePersistence = false })

        assertTrue { fixture.sentryOptions.scopeObservers.none { it is PersistingScopeObserver } }
    }
}
