package io.sentry.samples.android

import android.app.Application

object SentryBuddySampleIntegration {
  @JvmStatic @Suppress("UNUSED_PARAMETER") fun install(application: Application) = Unit

  fun isAvailable(): Boolean = false

  fun startCheckoutRecording() = Unit

  @Suppress("UNUSED_PARAMETER") fun recordStep(name: String) = Unit

  fun stopRecording(): String = "Sentry Buddy is only available in debug builds."
}
