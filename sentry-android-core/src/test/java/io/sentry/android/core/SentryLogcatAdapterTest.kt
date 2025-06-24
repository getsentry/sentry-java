package io.sentry.android.core

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SentryLogEvent
import io.sentry.SentryLogLevel
import io.sentry.SentryOptions
import io.sentry.android.core.performance.AppStartMetrics
import org.junit.runner.RunWith
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SentryLogcatAdapterTest {
    private val tag = "my-tag"
    private val commonMsg = "SentryLogcatAdapter"
    private val throwable = RuntimeException("Test Exception")

    class Fixture {
        val breadcrumbs = mutableListOf<Breadcrumb>()
        val logs = mutableListOf<SentryLogEvent>()

        fun initSut(
            options: Sentry.OptionsConfiguration<SentryAndroidOptions>? = null
        ) {
            val metadata = Bundle().apply {
                putString(ManifestMetadataReader.DSN, "https://key@sentry.io/123")
            }
            val mockContext = ContextUtilsTestHelper.mockMetaData(metaData = metadata)
            initForTest(mockContext) {
                it.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ ->
                    breadcrumbs.add(breadcrumb)
                    breadcrumb
                }
                it.logs.isEnabled = true
                it.logs.beforeSend = SentryOptions.Logs.BeforeSendLogCallback { logEvent ->
                    logs.add(logEvent)
                    logEvent
                }
                options?.configure(it)
            }
        }
    }

    private val fixture = Fixture()

    @AfterTest
    fun `clean up`() {
        AppStartMetrics.getInstance().clear()
        ContextUtils.resetInstance()
        Sentry.close()
        fixture.breadcrumbs.clear()
        fixture.logs.clear()
    }

    @Test
    fun `verbose log message has expected content`() {
        fixture.initSut()
        SentryLogcatAdapter.v(tag, "$commonMsg verbose")
        fixture.breadcrumbs.first().assert(tag, "$commonMsg verbose", SentryLevel.DEBUG)
        fixture.logs.first().assert("$commonMsg verbose", SentryLogLevel.TRACE)
    }

    @Test
    fun `info log message has expected content`() {
        fixture.initSut()
        SentryLogcatAdapter.i(tag, "$commonMsg info")
        fixture.breadcrumbs.first().assert(tag, "$commonMsg info", SentryLevel.INFO)
        fixture.logs.first().assert("$commonMsg info", SentryLogLevel.INFO)
    }

    @Test
    fun `debug log message has expected content`() {
        fixture.initSut()
        SentryLogcatAdapter.d(tag, "$commonMsg debug")
        fixture.breadcrumbs.first().assert(tag, "$commonMsg debug", SentryLevel.DEBUG)
        fixture.logs.first().assert("$commonMsg debug", SentryLogLevel.DEBUG)
    }

    @Test
    fun `warning log message has expected content`() {
        fixture.initSut()
        SentryLogcatAdapter.w(tag, "$commonMsg warning")
        fixture.breadcrumbs.first().assert(tag, "$commonMsg warning", SentryLevel.WARNING)
        fixture.logs.first().assert("$commonMsg warning", SentryLogLevel.WARN)
    }

    @Test
    fun `error log message has expected content`() {
        fixture.initSut()
        SentryLogcatAdapter.e(tag, "$commonMsg error")
        fixture.breadcrumbs.first().assert(tag, "$commonMsg error", SentryLevel.ERROR)
        fixture.logs.first().assert("$commonMsg error", SentryLogLevel.ERROR)
    }

    @Test
    fun `wtf log message has expected content`() {
        fixture.initSut()
        SentryLogcatAdapter.wtf(tag, "$commonMsg wtf")
        fixture.breadcrumbs.first().assert(tag, "$commonMsg wtf", SentryLevel.ERROR)
        fixture.logs.first().assert("$commonMsg wtf", SentryLogLevel.FATAL)
    }

    @Test
    fun `e log throwable has expected content`() {
        fixture.initSut()
        SentryLogcatAdapter.e(tag, "$commonMsg error exception", throwable)
        fixture.breadcrumbs.first().assert(tag, "$commonMsg error exception", SentryLevel.ERROR)
        fixture.logs.first().assert("$commonMsg error exception\n${throwable.stackTraceToString()}", SentryLogLevel.ERROR)
    }

    @Test
    fun `v log throwable has expected content`() {
        fixture.initSut()
        SentryLogcatAdapter.v(tag, "$commonMsg verbose exception", throwable)
        fixture.breadcrumbs.first().assert(tag, "$commonMsg verbose exception", SentryLevel.DEBUG)
        fixture.logs.first().assert("$commonMsg verbose exception\n${throwable.stackTraceToString()}", SentryLogLevel.TRACE)
    }

    @Test
    fun `i log throwable has expected content`() {
        fixture.initSut()
        SentryLogcatAdapter.i(tag, "$commonMsg info exception", throwable)
        fixture.breadcrumbs.first().assert(tag, "$commonMsg info exception", SentryLevel.INFO)
        fixture.logs.first().assert("$commonMsg info exception\n${throwable.stackTraceToString()}", SentryLogLevel.INFO)
    }

    @Test
    fun `d log throwable has expected content`() {
        fixture.initSut()
        SentryLogcatAdapter.d(tag, "$commonMsg debug exception", throwable)
        fixture.breadcrumbs.first().assert(tag, "$commonMsg debug exception", SentryLevel.DEBUG)
        fixture.logs.first().assert("$commonMsg debug exception\n${throwable.stackTraceToString()}", SentryLogLevel.DEBUG)
    }

    @Test
    fun `w log throwable has expected content`() {
        fixture.initSut()
        SentryLogcatAdapter.w(tag, "$commonMsg warning exception", throwable)
        fixture.breadcrumbs.first().assert(tag, "$commonMsg warning exception", SentryLevel.WARNING)
        fixture.logs.first().assert("$commonMsg warning exception\n${throwable.stackTraceToString()}", SentryLogLevel.WARN)
    }

    @Test
    fun `wtf log throwable has expected content`() {
        fixture.initSut()
        SentryLogcatAdapter.wtf(tag, "$commonMsg wtf exception", throwable)
        fixture.breadcrumbs.first().assert(tag, "$commonMsg wtf exception", SentryLevel.ERROR)
        fixture.logs.first().assert("$commonMsg wtf exception\n${throwable.stackTraceToString()}", SentryLogLevel.FATAL)
    }

    @Test
    fun `do not send logs if logs is disabled`() {
        fixture.initSut { it.logs.isEnabled = false }

        SentryLogcatAdapter.v(tag, "$commonMsg verbose")
        SentryLogcatAdapter.i(tag, "$commonMsg info")
        SentryLogcatAdapter.d(tag, "$commonMsg debug")
        SentryLogcatAdapter.w(tag, "$commonMsg warning")
        SentryLogcatAdapter.e(tag, "$commonMsg error")
        SentryLogcatAdapter.wtf(tag, "$commonMsg wtf")
        SentryLogcatAdapter.e(tag, "$commonMsg error exception", throwable)
        SentryLogcatAdapter.v(tag, "$commonMsg verbose exception", throwable)
        SentryLogcatAdapter.i(tag, "$commonMsg info exception", throwable)
        SentryLogcatAdapter.d(tag, "$commonMsg debug exception", throwable)
        SentryLogcatAdapter.w(tag, "$commonMsg warning exception", throwable)
        SentryLogcatAdapter.wtf(tag, "$commonMsg wtf exception", throwable)

        assertTrue(fixture.logs.isEmpty())
    }

    @Test
    fun `logs add correct number of breadcrumb`() {
        fixture.initSut()
        SentryLogcatAdapter.v(tag, commonMsg)
        SentryLogcatAdapter.d(tag, commonMsg)
        SentryLogcatAdapter.i(tag, commonMsg)
        SentryLogcatAdapter.w(tag, commonMsg)
        SentryLogcatAdapter.e(tag, commonMsg)
        SentryLogcatAdapter.wtf(tag, commonMsg)
        assertEquals(
            6,
            fixture.breadcrumbs.filter {
                it.message?.contains("SentryLogcatAdapter") ?: false
            }.size
        )
    }

    private fun Breadcrumb.assert(
        expectedTag: String,
        expectedMessage: String,
        expectedLevel: SentryLevel
    ) {
        assertEquals(expectedMessage, message)
        assertEquals(expectedTag, data["tag"])
        assertEquals(expectedLevel, level)
        assertEquals("Logcat", category)
    }

    private fun SentryLogEvent.assert(
        expectedMessage: String,
        expectedLevel: SentryLogLevel
    ) {
        assertEquals(expectedMessage, body)
        assertEquals(expectedLevel, level)
    }
}
