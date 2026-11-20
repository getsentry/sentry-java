package io.sentry.uitest.android

import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import io.sentry.SentryLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 30)
class NonFatalAnrTest : BaseUiTest() {
  @Test
  fun blockingMainThreadSendsOneNonFatalAnrEvent() {
    initSentry(true) { it.nonFatalAnrSampleRate = 1.0 }
    relayIdlingResource.increment()

    launchActivity<EmptyActivity>().onActivity { Thread.sleep(2000) }

    relay.assert {
      assertFirstEnvelope {
        val event = it.assertEvent()
        it.assertNoOtherItems()

        assertEquals(SentryLevel.ERROR, event.level)
        val mechanismTypes = event.exceptions?.mapNotNull { e -> e.mechanism?.type }
        assertTrue(mechanismTypes?.contains("NonFatalANR") == true, "mechanisms: $mechanismTypes")
        assertTrue(event.exceptions!!.all { e -> e.mechanism?.isHandled == true })

        val context = event.contexts["non_fatal_anr"] as Map<*, *>
        val durationMs = assertNotNull(context["duration_ms"] as? Number)
        assertTrue(durationMs.toLong() >= 700, "duration_ms: $durationMs")
      }
      assertNoOtherEnvelopes()
    }
  }
}
