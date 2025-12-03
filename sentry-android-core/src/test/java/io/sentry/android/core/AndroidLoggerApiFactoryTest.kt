package io.sentry.android.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Scopes
import kotlin.test.assertIs
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class AndroidLoggerApiFactoryTest {

  @Test
  fun `factory creates AndroidLogger`() {
    val factory = AndroidLoggerApiFactory()
    val logger = factory.create(mock<Scopes>())
    assertIs<AndroidLoggerApi>(logger)
  }
}
