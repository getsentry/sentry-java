package io.sentry.samples.android

import android.app.Application
import io.sentry.android.buddy.SentryBuddy
import io.sentry.android.buddy.bridge.SentryBuddyHttpFlowAnalysesApi
import io.sentry.android.buddy.bridge.SentryBuddyHttpHealthCheckApi
import io.sentry.android.buddy.bridge.SentryBuddyHttpOpenUrlApi

object SentryBuddySampleIntegration {
  @JvmStatic
  fun install(application: Application) {
    SentryBuddy.install(application) {
      flowAnalysesApi = SentryBuddyHttpFlowAnalysesApi("http://10.0.2.2:8080")
      healthCheckApi = SentryBuddyHttpHealthCheckApi("http://10.0.2.2:8080")
      openUrlApi = SentryBuddyHttpOpenUrlApi("http://10.0.2.2:8080")
      sentryUiBaseUrl = "https://sentry-sdks.sentry.io"
      sentryUiOrganizationSlug = "sentry-sdks"
      sentryUiProjectId = "5428559"
    }
  }
}
