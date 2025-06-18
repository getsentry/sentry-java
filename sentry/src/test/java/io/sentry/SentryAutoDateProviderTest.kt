package io.sentry

import io.sentry.util.Platform
import io.sentry.util.PlatformTestManipulator
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class SentryAutoDateProviderTest {
    val javaNinePlus = Platform.isJavaNinePlus()

    @AfterTest
    fun restorePlatform() {
        PlatformTestManipulator.pretendJavaNinePlus(javaNinePlus)
    }

    @Test
    fun `uses SentryInstantDate on Java9+`() {
        PlatformTestManipulator.pretendJavaNinePlus(true)
        val now = SentryAutoDateProvider().now()
        assertTrue(now is SentryInstantDate)
    }

    @Test
    fun `uses SentryNanotimeDate on Java8`() {
        PlatformTestManipulator.pretendJavaNinePlus(false)
        val now = SentryAutoDateProvider().now()
        assertTrue(now is SentryNanotimeDate)
    }
}
