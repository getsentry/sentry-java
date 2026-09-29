package io.sentry.samples.android

import android.app.Application
import io.sentry.android.buddy.SentryBuddy
import io.sentry.android.buddy.bridge.SentryBuddyHttpFlowAnalysesApi
import io.sentry.android.buddy.bridge.SentryBuddyHttpHealthCheckApi
import io.sentry.android.buddy.bridge.SentryBuddyHttpLogIngestApi
import io.sentry.android.buddy.bridge.SentryBuddyHttpOpenFileApi
import io.sentry.android.buddy.bridge.SentryBuddyHttpOpenUrlApi

object SentryBuddySampleIntegration {
  private const val BUDDY_HOST = "http://10.0.2.2:8080"

  @JvmStatic
  fun install(application: Application) {
    SentryBuddy.install(application) {
      flowAnalysesApi = SentryBuddyHttpFlowAnalysesApi(BUDDY_HOST)
      healthCheckApi = SentryBuddyHttpHealthCheckApi(BUDDY_HOST)
      openUrlApi = SentryBuddyHttpOpenUrlApi(BUDDY_HOST)
      openFileApi = SentryBuddyHttpOpenFileApi(BUDDY_HOST)
      logIngestApi = SentryBuddyHttpLogIngestApi(BUDDY_HOST)
      sourceBasePath = BuildConfig.BUDDY_SOURCE_BASE_PATH
      sentryUiBaseUrl = "https://sentry-sdks.sentry.io"
      sentryUiOrganizationSlug = "sentry-sdks"
      sentryUiProjectId = "5428559"
    }
  }
}
