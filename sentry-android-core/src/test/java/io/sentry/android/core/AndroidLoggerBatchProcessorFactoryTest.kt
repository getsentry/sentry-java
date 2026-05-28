package io.sentry.android.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.SentryClient
import kotlin.test.Test
import kotlin.test.assertIs
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class AndroidLoggerBatchProcessorFactoryTest {

  @Test
  fun `create returns AndroidLoggerBatchProcessor instance`() {
    val factory = AndroidLoggerBatchProcessorFactory()
    val options = SentryAndroidOptions()
    val client: SentryClient = mock()

    val processor = factory.create(options, client)

    assertIs<AndroidLoggerBatchProcessor>(processor)
  }
}
