package io.sentry.samples.android

import android.app.Application
import io.sentry.android.buddy.BuddyFlowImportance
import io.sentry.android.buddy.BuddyFlowIntent
import io.sentry.android.buddy.BuddyFlowRecordingJsonSerializer
import io.sentry.android.buddy.SentryBuddy

object SentryBuddySampleIntegration {
  @JvmStatic
  fun install(application: Application) {
    SentryBuddy.install(application)
  }

  fun isAvailable(): Boolean = true

  fun startCheckoutRecording() {
    SentryBuddy.startRecording(
      BuddyFlowIntent(
        name = "Sample Checkout",
        developerGoal = "Understand how this sample flow maps to Sentry telemetry.",
        importance = BuddyFlowImportance.BUSINESS_CRITICAL,
        data =
          linkedMapOf(
            "surface" to "sentry-samples-android",
            "demo" to "hackweek-sentry-buddy",
          ),
      )
    )
  }

  fun recordStep(name: String) {
    SentryBuddy.recordStep(name)
  }

  fun stopRecording(): String =
    BuddyFlowRecordingJsonSerializer.serialize(SentryBuddy.stopRecording())
}
