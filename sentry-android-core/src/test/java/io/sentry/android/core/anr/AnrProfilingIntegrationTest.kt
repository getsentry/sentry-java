package io.sentry.android.core.anr

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.android.core.hang.HangRecorder
import io.sentry.android.core.hang.HangRecorderListener
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

@RunWith(AndroidJUnit4::class)
class AnrProfilingIntegrationTest {

  @get:Rule val tmpDir = TemporaryFolder()

  private val scopes = mock<IScopes>()
  private val mockLogger = mock<ILogger>()
  private val recorder = mock<HangRecorder>()
  private lateinit var options: SentryAndroidOptions

  @BeforeTest
  fun setup() {
    SentryIntegrationPackageStorage.getInstance().clearStorage()
    options =
      SentryAndroidOptions().apply {
        cacheDirPath = tmpDir.root.absolutePath
        setLogger(mockLogger)
        isDebug = true
        anrProfilingSampleRate = 1.0
        hangRecorder = recorder
      }
  }

  @Test
  fun `register subscribes to the recorder when sampled`() {
    val integration = AnrProfilingIntegration()

    integration.register(scopes, options)

    verify(recorder, times(1)).subscribe(any())
    assertThat(SentryIntegrationPackageStorage.getInstance().integrations).contains("AnrProfiling")
  }

  @Test
  fun `register does not subscribe when not sampled`() {
    // Enabled, but only a draw of exactly 0.0 is below it.
    options.anrProfilingSampleRate = Double.MIN_VALUE
    val integration = AnrProfilingIntegration()

    integration.register(scopes, options)

    verify(recorder, never()).subscribe(any())
    assertThat(SentryIntegrationPackageStorage.getInstance().integrations)
      .doesNotContain("AnrProfiling")
  }

  @Test
  fun `register does not subscribe when ANR profiling is disabled`() {
    options.anrProfilingSampleRate = null
    val integration = AnrProfilingIntegration()

    integration.register(scopes, options)

    verify(recorder, never()).subscribe(any())
  }

  @Test
  fun `register does not subscribe when cacheDirPath is null`() {
    options.cacheDirPath = null
    val integration = AnrProfilingIntegration()

    integration.register(scopes, options)

    verify(recorder, never()).subscribe(any())
    verify(mockLogger)
      .log(SentryLevel.WARNING, "ANR Profiling is enabled but cacheDirPath is not set")
  }

  @Test
  fun `register does nothing when there is no recorder`() {
    options.hangRecorder = null
    val integration = AnrProfilingIntegration()

    integration.register(scopes, options)
    integration.close()

    assertThat(SentryIntegrationPackageStorage.getInstance().integrations)
      .doesNotContain("AnrProfiling")
  }

  @Test
  fun `register requires SentryAndroidOptions`() {
    val integration = AnrProfilingIntegration()

    assertFailsWith<IllegalArgumentException> { integration.register(scopes, SentryOptions()) }
  }

  @Test
  fun `close unsubscribes the listener it subscribed`() {
    val integration = AnrProfilingIntegration()
    integration.register(scopes, options)

    integration.close()

    val subscribed = argumentCaptor<HangRecorderListener>()
    verify(recorder).subscribe(subscribed.capture())
    verify(recorder).unsubscribe(subscribed.firstValue)
  }

  @Test
  fun `close unsubscribes only once`() {
    val integration = AnrProfilingIntegration()
    integration.register(scopes, options)

    integration.close()
    integration.close()

    verify(recorder, times(1)).unsubscribe(any())
  }

  @Test
  fun `close does not unsubscribe when not subscribed`() {
    options.anrProfilingSampleRate = Double.MIN_VALUE
    val integration = AnrProfilingIntegration()
    integration.register(scopes, options)

    integration.close()

    verify(recorder, never()).unsubscribe(any())
  }

  @Test
  fun `two integrations on one recorder use distinct subscriptions`() {
    val first = AnrProfilingIntegration()
    val second = AnrProfilingIntegration()
    first.register(scopes, options)
    second.register(scopes, options)

    val subscribed = argumentCaptor<HangRecorderListener>()
    verify(recorder, times(2)).subscribe(subscribed.capture())
    assertThat(subscribed.firstValue).isNotSameInstanceAs(subscribed.secondValue)
  }

  @Test
  @Suppress("DEPRECATION")
  fun `deprecated lifecycle and run methods do nothing`() {
    val integration = AnrProfilingIntegration()
    integration.register(scopes, options)
    verify(recorder).subscribe(any())

    integration.onForeground()
    integration.onBackground()
    integration.run()

    verifyNoMoreInteractions(recorder)
  }
}
