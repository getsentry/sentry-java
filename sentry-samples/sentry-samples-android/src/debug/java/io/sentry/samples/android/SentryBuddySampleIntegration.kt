package io.sentry.samples.android

import android.app.Application
import io.sentry.android.buddy.SentryBuddy

object SentryBuddySampleIntegration {
  @JvmStatic
  fun install(application: Application) {
    SentryBuddy.install(application)
  }
}
