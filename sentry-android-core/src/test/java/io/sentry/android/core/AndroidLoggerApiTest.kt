package io.sentry.android.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Scopes
import io.sentry.SentryClient
import io.sentry.SentryOptions
import io.sentry.test.ImmediateExecutorService
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AndroidLoggerApiTest {

  @Before
  fun setup() {
    AppState.getInstance().resetInstance()
  }

  @Test
  fun `AndroidLogger registers and unregisters app state listener`() {
    val scopes = mock<Scopes>()
    val logger = AndroidLoggerApi(scopes)
    assertTrue(AppState.getInstance().lifecycleObserver.listeners.isNotEmpty())

    logger.close()
    assertTrue(AppState.getInstance().lifecycleObserver.listeners.isEmpty())
  }

  @Test
  fun `AndroidLogger triggers flushing if app goes in background`() {
    val scopes = mock<Scopes>()

    val client = mock<SentryClient>()
    whenever(scopes.client).thenReturn(client)

    val options = SentryOptions()
    options.executorService = ImmediateExecutorService()
    whenever(scopes.options).thenReturn(options)

    val logger = AndroidLoggerApi(scopes)
    logger.onBackground()

    verify(client).flushLogs(any())
  }
}
