package io.sentry.android.core

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.ILogger
import io.sentry.MainEventProcessor
import io.sentry.SendCachedEnvelopeFireAndForgetIntegration
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.NdkIntegration.SENTRY_NDK_CLASS_NAME
import org.junit.runner.RunWith
import java.io.File
import java.lang.RuntimeException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidOptionsInitializerTest {
    private lateinit var context: Context
    private lateinit var file: File

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
        file = context.cacheDir
    }

    @Test
    fun `logger set to AndroidLogger`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()

        AndroidOptionsInitializer.init(sentryOptions, mockContext)
        val logger = SentryOptions::class.java.declaredFields.first { it.name == "logger" }
        logger.isAccessible = true
        val loggerField = logger.get(sentryOptions)
        val innerLogger = loggerField.javaClass.declaredFields.first { it.name == "logger" }
        innerLogger.isAccessible = true
        assertTrue(innerLogger.get(loggerField) is AndroidLogger)
    }

    @Test
    fun `AndroidEventProcessor added to processors list`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()

        AndroidOptionsInitializer.init(sentryOptions, mockContext)
        val actual = sentryOptions.eventProcessors.any { it is DefaultAndroidEventProcessor }
        assertNotNull(actual)
    }

    @Test
    fun `PerformanceAndroidEventProcessor added to processors list`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()

        AndroidOptionsInitializer.init(sentryOptions, mockContext)
        val actual = sentryOptions.eventProcessors.any { it is PerformanceAndroidEventProcessor }
        assertNotNull(actual)
    }

    @Test
    fun `MainEventProcessor added to processors list and its the 1st`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()

        AndroidOptionsInitializer.init(sentryOptions, mockContext)
        val actual = sentryOptions.eventProcessors.firstOrNull { it is MainEventProcessor }
        assertNotNull(actual)
    }

    @Test
    fun `envelopesDir should be set at initialization`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()

        AndroidOptionsInitializer.init(sentryOptions, mockContext)

        assertTrue(sentryOptions.cacheDirPath?.endsWith("${File.separator}cache${File.separator}sentry")!!)
    }

    @Test
    fun `outboxDir should be set at initialization`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()

        AndroidOptionsInitializer.init(sentryOptions, mockContext)

        assertTrue(sentryOptions.outboxPath?.endsWith("${File.separator}cache${File.separator}sentry${File.separator}outbox")!!)
    }

    @Test
    fun `init should set context package name as appInclude`() {
        val sentryOptions = SentryAndroidOptions()

        AndroidOptionsInitializer.init(sentryOptions, context)

        // cant mock PackageInfo, its buggy
        assertTrue(sentryOptions.inAppIncludes.contains("io.sentry.android.core.test"))
    }

    @Test
    fun `init should set release if empty`() {
        val sentryOptions = SentryAndroidOptions()

        AndroidOptionsInitializer.init(sentryOptions, context)

        // cant mock PackageInfo, its buggy
        assertTrue(sentryOptions.release!!.startsWith("io.sentry.android.core.test@"))
    }

    @Test
    fun `init should not replace options if set on manifest`() {
        val sentryOptions = SentryAndroidOptions().apply {
            release = "release"
        }

        AndroidOptionsInitializer.init(sentryOptions, context)

        assertEquals("release", sentryOptions.release)
    }

    @Test
    fun `init should not set context package name if it starts with android package`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = ContextUtilsTest.createMockContext()
        whenever(mockContext.packageName).thenReturn("android.context")

        AndroidOptionsInitializer.init(sentryOptions, mockContext)

        assertFalse(sentryOptions.inAppIncludes.contains("android.context"))
    }

    @Test
    fun `init should set distinct id on start`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = ContextUtilsTest.createMockContext()

        AndroidOptionsInitializer.init(sentryOptions, mockContext)

        assertNotNull(sentryOptions.distinctId) {
            assertTrue(it.isNotEmpty())
        }

        val installation = File(context.filesDir, Installation.INSTALLATION)
        installation.deleteOnExit()
    }

    @Test
    fun `init should set proguard uuid id on start`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = ContextUtilsTest.mockMetaData(metaData = createBundleWithProguardUuid())

        AndroidOptionsInitializer.init(sentryOptions, mockContext)

        assertEquals("proguard-uuid", sentryOptions.proguardUuid)
    }

    @Test
    fun `init should set Android transport gate`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()

        AndroidOptionsInitializer.init(sentryOptions, mockContext)

        assertNotNull(sentryOptions.transportGate)
        assertTrue(sentryOptions.transportGate is AndroidTransportGate)
    }

    @Test
    fun `NdkIntegration will load SentryNdk class and add to the integration list`() {
        val mockContext = ContextUtilsTest.mockMetaData(metaData = createBundleWithDsn())
        val logger = mock<ILogger>()
        val sentryOptions = SentryAndroidOptions().apply {
            setDebug(true)
        }

        AndroidOptionsInitializer.init(sentryOptions, mockContext, logger, createBuildInfo(), createClassMock())

        val actual = sentryOptions.integrations.firstOrNull { it is NdkIntegration }
        assertNotNull((actual as NdkIntegration).sentryNdkClass)

        verify(logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
        verify(logger, never()).log(eq(SentryLevel.FATAL), any<String>(), any())
    }

    @Test
    fun `NdkIntegration won't be enabled because API is lower than 16`() {
        val mockContext = ContextUtilsTest.mockMetaData(metaData = createBundleWithDsn())
        val logger = mock<ILogger>()
        val sentryOptions = SentryAndroidOptions().apply {
            setDebug(true)
        }

        AndroidOptionsInitializer.init(sentryOptions, mockContext, logger, createBuildInfo(14), createClassMock())

        val actual = sentryOptions.integrations.firstOrNull { it is NdkIntegration }
        assertNull((actual as NdkIntegration).sentryNdkClass)

        verify(logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
        verify(logger, never()).log(eq(SentryLevel.FATAL), any<String>(), any())
    }

    @Test
    fun `NdkIntegration won't be enabled, it throws linkage error`() {
        val mockContext = ContextUtilsTest.mockMetaData(metaData = createBundleWithDsn())
        val logger = mock<ILogger>()
        val sentryOptions = SentryAndroidOptions().apply {
            setDebug(true)
        }

        AndroidOptionsInitializer.init(sentryOptions, mockContext, logger, createBuildInfo(), createClassMockThrows(UnsatisfiedLinkError()))

        val actual = sentryOptions.integrations.firstOrNull { it is NdkIntegration }
        assertNull((actual as NdkIntegration).sentryNdkClass)

        verify(logger).log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `NdkIntegration won't be enabled, it throws class not found`() {
        val mockContext = ContextUtilsTest.mockMetaData(metaData = createBundleWithDsn())
        val logger = mock<ILogger>()
        val sentryOptions = SentryAndroidOptions().apply {
            setDebug(true)
        }

        AndroidOptionsInitializer.init(sentryOptions, mockContext, logger, createBuildInfo(), createClassMockThrows(ClassNotFoundException()))

        val actual = sentryOptions.integrations.firstOrNull { it is NdkIntegration }
        assertNull((actual as NdkIntegration).sentryNdkClass)

        verify(logger).log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `NdkIntegration won't be enabled, it throws unknown error`() {
        val mockContext = ContextUtilsTest.mockMetaData(metaData = createBundleWithDsn())
        val logger = mock<ILogger>()
        val sentryOptions = SentryAndroidOptions().apply {
            setDebug(true)
        }

        AndroidOptionsInitializer.init(sentryOptions, mockContext, logger, createBuildInfo(), createClassMockThrows(RuntimeException()))

        val actual = sentryOptions.integrations.firstOrNull { it is NdkIntegration }
        assertNull((actual as NdkIntegration).sentryNdkClass)

        verify(logger).log(eq(SentryLevel.ERROR), any<String>(), any())
    }

    @Test
    fun `AnrIntegration added to integration list`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()

        AndroidOptionsInitializer.init(sentryOptions, mockContext)
        val actual = sentryOptions.integrations.firstOrNull { it is AnrIntegration }
        assertNotNull(actual)
    }

    @Test
    fun `EnvelopeFileObserverIntegration added to integration list`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()

        AndroidOptionsInitializer.init(sentryOptions, mockContext)
        val actual = sentryOptions.integrations.firstOrNull { it is EnvelopeFileObserverIntegration }
        assertNotNull(actual)
    }

    @Test
    fun `SendCachedEnvelopeFireAndForgetIntegration added to integration list`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()

        AndroidOptionsInitializer.init(sentryOptions, mockContext)
        val actual = sentryOptions.integrations.firstOrNull { it is SendCachedEnvelopeFireAndForgetIntegration }
        assertNotNull(actual)
    }

    @Test
    fun `When given Context returns a non null ApplicationContext, uses it`() {
        val sentryOptions = SentryAndroidOptions()
        val mockApp = mock<Application>()
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockApp)

        AndroidOptionsInitializer.init(sentryOptions, mockContext)
        assertNotNull(mockContext)
    }

    @Test
    fun `When given Context returns a null ApplicationContext is null, keep given Context`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(null)

        AndroidOptionsInitializer.init(sentryOptions, mockContext)
        assertNotNull(mockContext)
    }

    @Test
    fun `When given Context is not an Application class, do not add ActivityBreadcrumbsIntegration`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(null)

        AndroidOptionsInitializer.init(sentryOptions, mockContext)
        val actual = sentryOptions.integrations.firstOrNull { it is ActivityLifecycleIntegration }
        assertNull(actual)
    }

    private fun createMockContext(): Context {
        val mockContext = ContextUtilsTest.createMockContext()
        whenever(mockContext.cacheDir).thenReturn(file)
        return mockContext
    }

    private fun createBundleWithDsn(): Bundle {
        return Bundle().apply {
            putString(ManifestMetadataReader.DSN, "https://key@sentry.io/123")
        }
    }

    private fun createBundleWithProguardUuid(): Bundle {
        return Bundle().apply {
            putString(ManifestMetadataReader.PROGUARD_UUID, "proguard-uuid")
        }
    }

    private fun createBuildInfo(minApi: Int = 16): IBuildInfoProvider {
        val buildInfo = mock<IBuildInfoProvider>()
        whenever(buildInfo.sdkInfoVersion).thenReturn(minApi)
        return buildInfo
    }

    private fun createClassMock(clazz: Class<*> = SentryNdk::class.java): LoadClass {
        val loadClassMock = mock<LoadClass>()
        whenever(loadClassMock.loadClass(any())).thenReturn(clazz)
        return loadClassMock
    }

    private fun createClassMockThrows(ex: Throwable): LoadClass {
        val loadClassMock = mock<LoadClass>()
        whenever(loadClassMock.loadClass(eq(SENTRY_NDK_CLASS_NAME))).thenThrow(ex)
        return loadClassMock
    }
}
