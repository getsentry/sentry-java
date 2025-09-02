package io.sentry.uitest.android

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ProfilingTraceData
import io.sentry.Sentry
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.android.core.AndroidLogger
import io.sentry.android.core.CurrentActivityHolder
import io.sentry.android.core.NdkIntegration
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.assertEnvelopeTransaction
import io.sentry.protocol.SentryTransaction
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import leakcanary.LeakAssertions
import leakcanary.LeakCanary
import org.junit.runner.RunWith
import shark.AndroidReferenceMatchers
import shark.IgnoredReferenceMatcher
import shark.ReferencePattern

@RunWith(AndroidJUnit4::class)
class SdkInitTests : BaseUiTest() {
  @Test
  fun doubleInitDoesNotThrow() {
    initSentry(false) { options: SentryAndroidOptions ->
      options.tracesSampleRate = 1.0
      options.profilesSampleRate = 1.0
    }
    val transaction = Sentry.startTransaction("e2etests", "testInit")
    val sampleScenario = launchActivity<EmptyActivity>()
    initSentry(false) { options: SentryAndroidOptions ->
      options.tracesSampleRate = 1.0
      options.profilesSampleRate = 1.0
    }
    transaction.finish()
    sampleScenario.moveToState(Lifecycle.State.DESTROYED)
    val transaction2 = Sentry.startTransaction("e2etests", "testInit")
    transaction2.finish()
  }

  @Test
  fun doubleInitWithSameOptionsDoesNotThrow() {
    val options = SentryAndroidOptions()

    initSentry(true) {
      it.tracesSampleRate = 1.0
      it.profilesSampleRate = 1.0
      // We use the same executorService before and after closing the SDK
      it.executorService = options.executorService
      it.isDebug = true
    }
    val transaction = Sentry.startTransaction("e2etests", "testInit")
    val sampleScenario = launchActivity<EmptyActivity>()

    initSentry(true) {
      it.tracesSampleRate = 1.0
      it.profilesSampleRate = 1.0
      // We use the same executorService before and after closing the SDK
      it.executorService = options.executorService
      it.isDebug = true
    }

    relayIdlingResource.increment()
    relayIdlingResource.increment()
    relayIdlingResource.increment()
    transaction.finish()
    sampleScenario.moveToState(Lifecycle.State.DESTROYED)
    val transaction2 = Sentry.startTransaction("e2etests2", "testInit")
    transaction2.finish()

    relay.assert {
      findEnvelope {
          assertEnvelopeTransaction(it.items.toList(), AndroidLogger()).transaction == "e2etests"
        }
        .assert {
          val transactionItem: SentryTransaction = it.assertTransaction()
          it.assertNoOtherItems()
          assertEquals("e2etests", transactionItem.transaction)
        }

      findEnvelope {
          assertEnvelopeTransaction(it.items.toList(), AndroidLogger()).transaction ==
            "EmptyActivity"
        }
        .assert {
          val transactionItem: SentryTransaction = it.assertTransaction()
          // Transaction-based Profiling is already in e2etests transaction, so it won't run again
          // here
          it.assertNoOtherItems()
          assertEquals("EmptyActivity", transactionItem.transaction)
        }

      findEnvelope {
          assertEnvelopeTransaction(it.items.toList(), AndroidLogger()).transaction == "e2etests2"
        }
        .assert {
          val transactionItem: SentryTransaction = it.assertTransaction()
          // Profiling uses executorService, so if the executorService is shutdown it would fail
          val profilingTraceData: ProfilingTraceData = it.assertProfile()
          it.assertNoOtherItems()
          assertEquals("e2etests2", transactionItem.transaction)
          assertEquals("e2etests2", profilingTraceData.transactionName)
        }
      assertNoOtherEnvelopes()
    }
  }

  @Test
  fun doubleInitDoesNotWait() {
    relayIdlingResource.increment()
    // Let's make the first request timeout
    relay.addTimeoutResponse()

    initSentry(true) { options: SentryAndroidOptions -> options.tracesSampleRate = 1.0 }

    Sentry.startTransaction("beforeRestart", "emptyTransaction").finish()

    // We want the SDK to start sending the event. If we don't wait, it's possible we don't send
    // anything before the SDK is restarted
    waitUntilIdle()

    relayIdlingResource.increment()
    relayIdlingResource.increment()

    val beforeRestart = System.currentTimeMillis()
    // We restart the SDK. This shouldn't block the main thread, but new options (e.g. profiling)
    // should work
    initSentry(true) { options: SentryAndroidOptions ->
      options.tracesSampleRate = 1.0
      options.profilesSampleRate = 1.0
    }
    val afterRestart = System.currentTimeMillis()
    val restartMs = afterRestart - beforeRestart

    Sentry.startTransaction("afterRestart", "emptyTransaction").finish()
    // We assert for less than 1 second just to account for slow devices in saucelabs or headless
    // emulator
    assertTrue(restartMs < 1000, "Expected less than 1000 ms for SDK restart. Got $restartMs ms")

    relay.assert {
      findEnvelope { assertEnvelopeTransaction(it.items.toList()).transaction == "beforeRestart" }
        .assert {
          it.assertTransaction()
          // No profiling item, as in the first init it was not enabled
          it.assertNoOtherItems()
        }
      findEnvelope { assertEnvelopeTransaction(it.items.toList()).transaction == "afterRestart" }
        .assert {
          it.assertTransaction()
          // There is a profiling item, as in the second init it was enabled
          it.assertProfile()
          it.assertNoOtherItems()
        }
      assertNoOtherEnvelopes()
    }
    context.cacheDir.deleteRecursively()
    context.cacheDir.mkdirs()
  }

  @Test
  fun initCloseInitWaits() {
    relayIdlingResource.increment()
    // Let's make the first request timeout
    relay.addTimeoutResponse()

    initSentry(true) { options: SentryAndroidOptions ->
      options.tracesSampleRate = 1.0
      options.flushTimeoutMillis = 3000
    }

    Sentry.startTransaction("beforeRestart", "emptyTransaction").finish()

    // We want the SDK to start sending the event. If we don't wait, it's possible we don't send
    // anything before the SDK is restarted
    waitUntilIdle()

    val beforeRestart = System.currentTimeMillis()
    Sentry.close()
    // We stop the SDK. This should block the main thread. Then we start it again with new options
    initSentry(true) { options: SentryAndroidOptions ->
      options.tracesSampleRate = 1.0
      options.profilesSampleRate = 1.0
    }
    val afterRestart = System.currentTimeMillis()
    val restartMs = afterRestart - beforeRestart
    assertTrue(
      restartMs > 3000,
      "Expected more than 3000 ms for SDK close and restart. Got $restartMs ms",
    )
  }

  @Test
  fun initViaActivityDoesNotLeak() {
    LeakCanary.config =
      LeakCanary.config.copy(
        referenceMatchers =
          AndroidReferenceMatchers.appDefaults +
            listOf(
              IgnoredReferenceMatcher(
                ReferencePattern.InstanceFieldPattern(
                  "com.saucelabs.rdcinjector.testfairy.TestFairyEventQueue",
                  "context",
                )
              )
            ) +
            ('a'..'z').map { char ->
              IgnoredReferenceMatcher(
                ReferencePattern.StaticFieldPattern(
                  "com.testfairy.modules.capture.TouchListener",
                  "$char",
                )
              )
            }
      )

    val activityScenario = launchActivity<ComposeActivity>()
    activityScenario.moveToState(Lifecycle.State.RESUMED)

    activityScenario.onActivity { activity -> initSentry(context = activity) }

    activityScenario.moveToState(Lifecycle.State.DESTROYED)

    LeakAssertions.assertNoLeaks()
  }

  @Test
  fun foregroundInitInstallsDefaultIntegrations() {
    val activityScenario = launchActivity<ComposeActivity>()
    activityScenario.moveToState(Lifecycle.State.RESUMED)
    activityScenario.onActivity { activity ->
      // Our SentryInitProvider does not run in this test
      // so we need to set the current activity manually
      CurrentActivityHolder.getInstance().setActivity(activity)
      initSentry(false) { options: SentryAndroidOptions ->
        options.tracesSampleRate = 1.0
        options.profilesSampleRate = 1.0
      }
    }
    activityScenario.moveToState(Lifecycle.State.DESTROYED)
    assertDefaultIntegrations()
  }

  @Test
  fun backgroundInitInstallsDefaultIntegrations() {
    val initLatch = CountDownLatch(1)

    val activityScenario = launchActivity<ComposeActivity>()
    activityScenario.moveToState(Lifecycle.State.RESUMED)
    activityScenario.onActivity { activity ->
      // Our SentryInitProvider does not run in this test
      // so we need to set the current activity manually
      CurrentActivityHolder.getInstance().setActivity(activity)
      Thread {
          initSentry(false) { options: SentryAndroidOptions ->
            options.tracesSampleRate = 1.0
            options.profilesSampleRate = 1.0
          }
          initLatch.countDown()
        }
        .start()
    }
    initLatch.await()

    activityScenario.moveToState(Lifecycle.State.DESTROYED)

    assertDefaultIntegrations()
  }

  private fun assertDefaultIntegrations() {
    val integrations =
      mutableListOf(
        "UncaughtExceptionHandler",
        "ShutdownHook",
        "SendCachedEnvelope",
        "AppLifecycle",
        "EnvelopeFileObserver",
        "AnrV2",
        "ActivityLifecycle",
        "ActivityBreadcrumbs",
        "UserInteraction",
        "AppComponentsBreadcrumbs",
        "NetworkBreadcrumbs",
      )

    // NdkIntegration is not always available, so we check for its presence
    try {
      Class.forName(NdkIntegration.SENTRY_NDK_CLASS_NAME)
      integrations.add("Ndk")
    } catch (_: ClassNotFoundException) {
      // ignored, in case the app is build without NDK support
    }

    for (integration in integrations) {
      assertTrue(
        SentryIntegrationPackageStorage.getInstance().integrations.contains(integration),
        "Integration $integration was expected, but was not registered",
      )
    }
  }
}
