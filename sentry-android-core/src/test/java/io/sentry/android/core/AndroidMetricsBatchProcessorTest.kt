package io.sentry.android.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.ISentryClient
import io.sentry.SentryMetricsEvent
import io.sentry.SentryOptions
import io.sentry.protocol.SentryId
import io.sentry.test.ImmediateExecutorService
import io.sentry.test.getProperty
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AndroidMetricsBatchProcessorTest {

  private class Fixture {
    val options = SentryAndroidOptions()
    val client: ISentryClient = mock()

    fun getSut(
      useImmediateExecutor: Boolean = false,
      config: ((SentryOptions) -> Unit)? = null,
    ): AndroidMetricsBatchProcessor {
      if (useImmediateExecutor) {
        options.executorService = ImmediateExecutorService()
      }
      config?.invoke(options)
      return AndroidMetricsBatchProcessor(options, client)
    }
  }

  private val fixture = Fixture()

  @BeforeTest
  fun `set up`() {
    AppState.getInstance().resetInstance()
  }

  @AfterTest
  fun `tear down`() {
    AppState.getInstance().resetInstance()
  }

  @Test
  fun `constructor registers as AppState listener`() {
    fixture.getSut()
    assertNotNull(AppState.getInstance().lifecycleObserver)
  }

  @Test
  fun `onBackground does not flush before first accepted item`() {
    val sut = fixture.getSut(useImmediateExecutor = true)

    sut.onBackground()

    assertThat(sut.getProperty<AtomicBoolean>("hasScheduled").get()).isFalse()
    verify(fixture.client, never()).captureBatchedMetricsEvents(any())
  }

  @Test
  fun `onBackground schedules flush`() {
    val sut = fixture.getSut(useImmediateExecutor = true)
    val metricsEvent = SentryMetricsEvent(SentryId(), 1.0, "test", "counter", 3.0)
    sut.add(metricsEvent)

    sut.onBackground()

    verify(fixture.client).captureBatchedMetricsEvents(any())
  }

  @Test
  fun `onBackground handles executor exception gracefully`() {
    val sut = fixture.getSut { options ->
      val rejectingExecutor = mock<io.sentry.ISentryExecutorService>()
      whenever(rejectingExecutor.submit(any())).thenThrow(RuntimeException("Rejected"))
      options.executorService = rejectingExecutor
    }

    // Should not throw
    sut.onBackground()
  }

  @Test
  fun `close removes AppState listener`() {
    val sut = fixture.getSut()
    sut.close(false)

    assertTrue(AppState.getInstance().lifecycleObserver.listeners.isEmpty())
  }

  @Test
  fun `close with isRestarting true still removes listener`() {
    val sut = fixture.getSut()
    sut.close(true)

    assertTrue(AppState.getInstance().lifecycleObserver.listeners.isEmpty())
  }
}
