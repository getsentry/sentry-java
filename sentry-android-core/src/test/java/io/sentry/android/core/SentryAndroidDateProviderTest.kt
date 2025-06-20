package io.sentry.android.core

import io.sentry.SentryNanotimeDate
import kotlin.test.Test
import kotlin.test.assertTrue

class SentryAndroidDateProviderTest {
  @Test
  fun `provides SentryInstantDate on newer Android API levels`() {
    val date = SentryAndroidDateProvider().now()
    assertTrue(date is SentryNanotimeDate)
  }
}
