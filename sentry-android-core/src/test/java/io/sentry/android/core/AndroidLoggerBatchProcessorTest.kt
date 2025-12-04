package io.sentry.android.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ISentryClient
import io.sentry.SentryLogEvent
import io.sentry.SentryLogLevel
import io.sentry.protocol.SentryId
import io.sentry.test.ImmediateExecutorService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AndroidLoggerBatchProcessorTest {

  private class Fixture {
    val options = SentryAndroidOptions()
    val client: ISentryClient = mock()

    fun getSut(useImmediateExecutor: Boolean = false): AndroidLoggerBatchProcessor {
      if (useImmediateExecutor) {
        options.executorService = ImmediateExecutorService()
      }
      return AndroidLoggerBatchProcessor(options, client)
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
  fun `onBackground schedules flush`() {
    val sut = fixture.getSut(useImmediateExecutor = true)
    val logEvent = SentryLogEvent(SentryId(), 1.0, "test", SentryLogLevel.INFO)
    sut.add(logEvent)

    sut.onBackground()

    verify(fixture.client).captureBatchedLogEvents(any())
  }

  @Test
  fun `onBackground handles executor exception gracefully`() {
    val options = SentryAndroidOptions()
    // Use a rejecting executor
    val rejectingExecutor = mock<io.sentry.ISentryExecutorService>()
    whenever(rejectingExecutor.submit(any())).thenThrow(RuntimeException("Rejected"))
    options.executorService = rejectingExecutor

    val sut = AndroidLoggerBatchProcessor(options, fixture.client)

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
