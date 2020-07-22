package io.sentry.android.core

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import io.sentry.core.ILogger
import io.sentry.core.SentryLevel
import io.sentry.core.SentryOptions
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NdkIntegrationTest {

    private class Fixture {
        fun getSut(clazz: Class<*>? = SentryNdk::class.java): NdkIntegration {
            return NdkIntegration(clazz)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `NdkIntegration calls init method`() {
        val integration = fixture.getSut()

        val logger = mock<ILogger>()
        val options = getOptions(logger = logger)

        integration.register(mock(), options)

        verify(logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())
        assertTrue(options.isEnableNdk)
    }

    @Test
    fun `NdkIntegration won't init if ndk integration is disabled`() {
        val integration = fixture.getSut()

        val logger = mock<ILogger>()
        val options = getOptions(enableNdk = false, logger = logger)

        integration.register(mock(), options)

        verify(logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())

        assertFalse(options.isEnableNdk)
    }

    @Test
    fun `NdkIntegration won't init if SentryNdk class is not available`() {
        val integration = fixture.getSut(null)

        val logger = mock<ILogger>()
        val options = getOptions(logger = logger)

        integration.register(mock(), options)

        verify(logger, never()).log(eq(SentryLevel.ERROR), any<String>(), any())

        assertFalse(options.isEnableNdk)
    }

    @Test
    fun `NdkIntegration won't init if init method is not available`() {
        val integration = fixture.getSut(SentryNdkNoInit::class.java)

        val logger = mock<ILogger>()
        val options = getOptions(logger = logger)

        integration.register(mock(), options)

        verify(logger).log(eq(SentryLevel.ERROR), any<String>(), any())

        assertFalse(options.isEnableNdk)
    }

    @Test
    fun `NdkIntegration won't init if init throws`() {
        val integration = fixture.getSut(SentryNdkThrows::class.java)

        val logger = mock<ILogger>()
        val options = getOptions(logger = logger)

        integration.register(mock(), options)

        verify(logger).log(eq(SentryLevel.ERROR), any<String>(), any())

        assertFalse(options.isEnableNdk)
    }

    @Test
    fun `NdkIntegration won't init if cache dir is null`() {
        val integration = fixture.getSut()

        val logger = mock<ILogger>()
        val options = getOptions(logger = logger, cacheDir = null)

        integration.register(mock(), options)

        verify(logger).log(eq(SentryLevel.ERROR), any())

        assertFalse(options.isEnableNdk)
    }

    @Test
    fun `NdkIntegration won't init if cache dir is empty`() {
        val integration = fixture.getSut()

        val logger = mock<ILogger>()
        val options = getOptions(logger = logger, cacheDir = "")

        integration.register(mock(), options)

        verify(logger).log(eq(SentryLevel.ERROR), any())

        assertFalse(options.isEnableNdk)
    }

    private fun getOptions(enableNdk: Boolean = true, logger: ILogger = mock(), cacheDir: String? = "abc"): SentryOptions {
        return SentryOptions().apply {
            setLogger(logger)
            isDebug = true
            isEnableNdk = enableNdk
            cacheDirPath = cacheDir
        }
    }

    private class SentryNdkNoInit

    private class SentryNdkThrows {
        companion object {
            @JvmStatic
            fun init(options: SentryOptions) {
                throw RuntimeException("damn")
            }
        }
    }
}
