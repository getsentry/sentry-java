package io.sentry.android.core

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SentryLogEvent
import io.sentry.SentryLogLevel
import io.sentry.SentryOptions
import io.sentry.android.core.performance.AppStartMetrics
import java.lang.RuntimeException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

@RunWith(AndroidJUnit4::class)
class SentryLogcatAdapterTest {
  private val tag = "my-tag"
  private val commonMsg = "SentryLogcatAdapter"
  private val throwable = RuntimeException("Test Exception")

  class Fixture {
    val breadcrumbs = mutableListOf<Breadcrumb>()
    val logs = mutableListOf<SentryLogEvent>()

    fun initSut(
      logcatLogsEnabled: Boolean? = true,
      metadata: Bundle = Bundle(),
      options: Sentry.OptionsConfiguration<SentryAndroidOptions>? = null,
    ) {
      metadata.putString(ManifestMetadataReader.DSN, "https://key@sentry.io/123")
      val mockContext = ContextUtilsTestHelper.mockMetaData(metaData = metadata)
      initForTest(mockContext) {
        it.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ ->
          breadcrumbs.add(breadcrumb)
          breadcrumb
        }
        if (logcatLogsEnabled != null) {
          it.logcatLogsEnabled = logcatLogsEnabled
        }
        it.logs.beforeSend =
          SentryOptions.Logs.BeforeSendLogCallback { logEvent ->
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
    ShadowLog.clear()
  }

  @Test
  fun `Logcat logs are disabled by default while breadcrumbs and Android Log remain enabled`() {
    fixture.initSut(logcatLogsEnabled = null)

    SentryLogcatAdapter.d(tag, commonMsg)

    assertThat(fixture.logs).isEmpty()
    assertThat(fixture.breadcrumbs).hasSize(1)
    assertThat(ShadowLog.getLogs().any { it.tag == tag && it.msg == commonMsg }).isTrue()
  }

  @Test
  fun `Logcat logs can be enabled through Android options`() {
    fixture.initSut(logcatLogsEnabled = true)

    SentryLogcatAdapter.d(tag, commonMsg)

    assertThat(fixture.logs).hasSize(1)
  }

  @Test
  fun `Logcat logs can be enabled through manifest metadata`() {
    val metadata = Bundle().apply { putBoolean(ManifestMetadataReader.ENABLE_LOGCAT_LOGS, true) }
    fixture.initSut(logcatLogsEnabled = null, metadata = metadata)

    SentryLogcatAdapter.d(tag, commonMsg)

    assertThat(fixture.logs).hasSize(1)
  }

  @Test
  fun `verbose log message has expected content`() {
    fixture.initSut()
    SentryLogcatAdapter.v(tag, "$commonMsg verbose")
    fixture.breadcrumbs.first().assert(tag, "$commonMsg verbose", SentryLevel.DEBUG)
    fixture.logs.first().assert("$commonMsg verbose", SentryLogLevel.TRACE)
    assertEquals("auto.log.logcat", fixture.logs.first().attributes?.get("sentry.origin")?.value)
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
    fixture.logs
      .first()
      .assert("$commonMsg error exception\n${throwable.message}", SentryLogLevel.ERROR)
  }

  @Test
  fun `v log throwable has expected content`() {
    fixture.initSut()
    SentryLogcatAdapter.v(tag, "$commonMsg verbose exception", throwable)
    fixture.breadcrumbs.first().assert(tag, "$commonMsg verbose exception", SentryLevel.DEBUG)
    fixture.logs
      .first()
      .assert("$commonMsg verbose exception\n${throwable.message}", SentryLogLevel.TRACE)
  }

  @Test
  fun `i log throwable has expected content`() {
    fixture.initSut()
    SentryLogcatAdapter.i(tag, "$commonMsg info exception", throwable)
    fixture.breadcrumbs.first().assert(tag, "$commonMsg info exception", SentryLevel.INFO)
    fixture.logs
      .first()
      .assert("$commonMsg info exception\n${throwable.message}", SentryLogLevel.INFO)
  }

  @Test
  fun `d log throwable has expected content`() {
    fixture.initSut()
    SentryLogcatAdapter.d(tag, "$commonMsg debug exception", throwable)
    fixture.breadcrumbs.first().assert(tag, "$commonMsg debug exception", SentryLevel.DEBUG)
    fixture.logs
      .first()
      .assert("$commonMsg debug exception\n${throwable.message}", SentryLogLevel.DEBUG)
  }

  @Test
  fun `w log throwable has expected content`() {
    fixture.initSut()
    SentryLogcatAdapter.w(tag, "$commonMsg warning exception", throwable)
    fixture.breadcrumbs.first().assert(tag, "$commonMsg warning exception", SentryLevel.WARNING)
    fixture.logs
      .first()
      .assert("$commonMsg warning exception\n${throwable.message}", SentryLogLevel.WARN)
  }

  @Test
  fun `wtf log throwable has expected content`() {
    fixture.initSut()
    SentryLogcatAdapter.wtf(tag, "$commonMsg wtf exception", throwable)
    fixture.breadcrumbs.first().assert(tag, "$commonMsg wtf exception", SentryLevel.ERROR)
    fixture.logs
      .first()
      .assert("$commonMsg wtf exception\n${throwable.message}", SentryLogLevel.FATAL)
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
      fixture.breadcrumbs.filter { it.message?.contains("SentryLogcatAdapter") ?: false }.size,
    )
  }

  private fun Breadcrumb.assert(
    expectedTag: String,
    expectedMessage: String,
    expectedLevel: SentryLevel,
  ) {
    assertEquals(expectedMessage, message)
    assertEquals(expectedTag, data["tag"])
    assertEquals(expectedLevel, level)
    assertEquals("Logcat", category)
  }

  private fun SentryLogEvent.assert(expectedMessage: String, expectedLevel: SentryLogLevel) {
    assertEquals(expectedMessage, body)
    assertEquals(expectedLevel, level)
  }
}
