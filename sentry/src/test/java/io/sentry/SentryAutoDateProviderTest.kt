package io.sentry

import io.sentry.util.Platform
import io.sentry.util.PlatformTestManipulator
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class SentryAutoDateProviderTest {
  val isAndroid = Platform.isAndroid()

  @AfterTest
  fun restorePlatform() {
    PlatformTestManipulator.pretendIsAndroid(isAndroid)
  }

  @Test
  fun `uses SentryInstantDate on the JVM`() {
    PlatformTestManipulator.pretendIsAndroid(false)
    val now = SentryAutoDateProvider().now()
    assertTrue(now is SentryInstantDate)
  }

  @Test
  fun `uses SentryNanotimeDate on Android`() {
    PlatformTestManipulator.pretendIsAndroid(true)
    val now = SentryAutoDateProvider().now()
    assertTrue(now is SentryNanotimeDate)
  }
}
