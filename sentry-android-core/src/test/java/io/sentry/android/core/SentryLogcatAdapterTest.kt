package io.sentry.android.core

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.performance.AppStartMetrics
import org.junit.runner.RunWith
import java.lang.RuntimeException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SentryLogcatAdapterTest {
    private val breadcrumbs = mutableListOf<Breadcrumb>()
    private val tag = "my-tag"
    private val commonMsg = "SentryLogcatAdapter"
    private val throwable = RuntimeException("Test Exception")

    class Fixture {
        fun initSut(options: Sentry.OptionsConfiguration<SentryAndroidOptions>? = null) {
            val metadata =
                Bundle().apply {
                    putString(ManifestMetadataReader.DSN, "https://key@sentry.io/123")
                }
            val mockContext = ContextUtilsTestHelper.mockMetaData(metaData = metadata)
            when {
                options != null -> initForTest(mockContext, options)
                else -> initForTest(mockContext)
            }
        }
    }

    private val fixture = Fixture()

    @BeforeTest
    fun `set up`() {
        Sentry.close()
        AppStartMetrics.getInstance().clear()
        ContextUtils.resetInstance()
        breadcrumbs.clear()

        fixture.initSut {
            it.beforeBreadcrumb =
                SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ ->
                    breadcrumbs.add(breadcrumb)
                    breadcrumb
                }
        }

        SentryLogcatAdapter.v(tag, "$commonMsg verbose")
        SentryLogcatAdapter.i(tag, "$commonMsg info")
        SentryLogcatAdapter.d(tag, "$commonMsg debug")
        SentryLogcatAdapter.w(tag, "$commonMsg warning")
        SentryLogcatAdapter.e(tag, "$commonMsg error")
        SentryLogcatAdapter.wtf(tag, "$commonMsg wtf")
    }

    @Test
    fun `verbose log message has expected content`() {
        val breadcrumb = breadcrumbs.find { it.level == SentryLevel.DEBUG && it.message?.contains("verbose") ?: false }
        assertEquals("Logcat", breadcrumb?.category)
        assertEquals(tag, breadcrumb?.data?.get("tag"))
        assert(breadcrumb?.message?.contains("verbose") == true)
    }

    @Test
    fun `info log message has expected content`() {
        val breadcrumb = breadcrumbs.find { it.level == SentryLevel.INFO && it.message?.contains("info") ?: false }
        assertEquals("Logcat", breadcrumb?.category)
        assertEquals(tag, breadcrumb?.data?.get("tag"))
        assert(breadcrumb?.message?.contains("info") == true)
    }

    @Test
    fun `debug log message has expected content`() {
        val breadcrumb = breadcrumbs.find { it.level == SentryLevel.DEBUG && it.message?.contains("debug") ?: false }
        assertEquals("Logcat", breadcrumb?.category)
        assertEquals(tag, breadcrumb?.data?.get("tag"))
        assert(breadcrumb?.message?.contains("debug") == true)
    }

    @Test
    fun `warning log message has expected content`() {
        val breadcrumb = breadcrumbs.find { it.level == SentryLevel.WARNING && it.message?.contains("warning") ?: false }
        assertEquals("Logcat", breadcrumb?.category)
        assertEquals(tag, breadcrumb?.data?.get("tag"))
        assert(breadcrumb?.message?.contains("warning") == true)
    }

    @Test
    fun `error log message has expected content`() {
        val breadcrumb = breadcrumbs.find { it.level == SentryLevel.ERROR && it.message?.contains("error") ?: false }
        assertEquals("Logcat", breadcrumb?.category)
        assertEquals(tag, breadcrumb?.data?.get("tag"))
        assert(breadcrumb?.message?.contains("error") == true)
    }

    @Test
    fun `wtf log message has expected content`() {
        val breadcrumb = breadcrumbs.find { it.level == SentryLevel.ERROR && it.message?.contains("wtf") ?: false }
        assertEquals("Logcat", breadcrumb?.category)
        assertEquals(tag, breadcrumb?.data?.get("tag"))
        assert(breadcrumb?.message?.contains("wtf") == true)
    }

    @Test
    fun `e log throwable has expected content`() {
        SentryLogcatAdapter.e(tag, "$commonMsg error exception", throwable)

        val breadcrumb = breadcrumbs.find { it.message?.contains("exception") ?: false }
        assertEquals("Logcat", breadcrumb?.category)
        assertEquals(tag, breadcrumb?.getData("tag"))
        assertEquals(SentryLevel.ERROR, breadcrumb?.level)
        assertEquals(throwable.message, breadcrumb?.getData("throwable"))
    }

    @Test
    fun `v log throwable has expected content`() {
        SentryLogcatAdapter.v(tag, "$commonMsg verbose exception", throwable)

        val breadcrumb = breadcrumbs.find { it.message?.contains("exception") ?: false }
        assertEquals("Logcat", breadcrumb?.category)
        assertEquals(tag, breadcrumb?.getData("tag"))
        assertEquals(SentryLevel.DEBUG, breadcrumb?.level)
        assertEquals(throwable.message, breadcrumb?.getData("throwable"))
    }

    @Test
    fun `i log throwable has expected content`() {
        SentryLogcatAdapter.i(tag, "$commonMsg info exception", throwable)

        val breadcrumb = breadcrumbs.find { it.message?.contains("exception") ?: false }
        assertEquals("Logcat", breadcrumb?.category)
        assertEquals(tag, breadcrumb?.getData("tag"))
        assertEquals(SentryLevel.INFO, breadcrumb?.level)
        assertEquals(throwable.message, breadcrumb?.getData("throwable"))
    }

    @Test
    fun `d log throwable has expected content`() {
        SentryLogcatAdapter.d(tag, "$commonMsg debug exception", throwable)

        val breadcrumb = breadcrumbs.find { it.message?.contains("exception") ?: false }
        assertEquals("Logcat", breadcrumb?.category)
        assertEquals(tag, breadcrumb?.getData("tag"))
        assertEquals(SentryLevel.DEBUG, breadcrumb?.level)
        assertEquals(throwable.message, breadcrumb?.getData("throwable"))
    }

    @Test
    fun `w log throwable has expected content`() {
        SentryLogcatAdapter.w(tag, "$commonMsg warning exception", throwable)

        val breadcrumb = breadcrumbs.find { it.message?.contains("exception") ?: false }
        assertEquals("Logcat", breadcrumb?.category)
        assertEquals(tag, breadcrumb?.getData("tag"))
        assertEquals(SentryLevel.WARNING, breadcrumb?.level)
        assertEquals(throwable.message, breadcrumb?.getData("throwable"))
    }

    @Test
    fun `wtf log throwable has expected content`() {
        SentryLogcatAdapter.wtf(tag, "$commonMsg wtf exception", throwable)

        val breadcrumb = breadcrumbs.find { it.message?.contains("exception") ?: false }
        assertEquals("Logcat", breadcrumb?.category)
        assertEquals(tag, breadcrumb?.getData("tag"))
        assertEquals(SentryLevel.ERROR, breadcrumb?.level)
        assertEquals(throwable.message, breadcrumb?.getData("throwable"))
    }

    @Test
    fun `logs add correct number of breadcrumb`() {
        assertEquals(
            6,
            breadcrumbs
                .filter {
                    it.message?.contains("SentryLogcatAdapter") ?: false
                }.size,
        )
    }
}
