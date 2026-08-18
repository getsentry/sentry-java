package io.sentry.samples.android

import android.app.Application
import io.sentry.android.buddy.SentryBuddy
import io.sentry.android.buddy.SentryBuddyHttpFlowAnalysesApi

object SentryBuddySampleIntegration {
  @JvmStatic
  fun install(application: Application) {
    SentryBuddy.install(application) {
      flowAnalysesApi = SentryBuddyHttpFlowAnalysesApi("http://10.0.2.2:8080")
    }
  }
}
