package io.sentry.android.buddy

import io.sentry.android.buddy.bridge.DummySentryBuddyFlowAnalysesApi
import io.sentry.android.buddy.bridge.DummySentryBuddyHealthCheckApi
import io.sentry.android.buddy.bridge.DummySentryBuddyLogIngestApi
import io.sentry.android.buddy.bridge.DummySentryBuddyOpenFileApi
import io.sentry.android.buddy.bridge.DummySentryBuddyOpenUrlApi
import io.sentry.android.buddy.bridge.SentryBuddyFlowAnalysesApi
import io.sentry.android.buddy.bridge.SentryBuddyHealthCheckApi
import io.sentry.android.buddy.bridge.SentryBuddyLogIngestApi
import io.sentry.android.buddy.bridge.SentryBuddyOpenFileApi
import io.sentry.android.buddy.bridge.SentryBuddyOpenUrlApi
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public class SentryBuddyOptions
@JvmOverloads
public constructor(
  public var enabled: Boolean = true,
  public var showOverlay: Boolean = true,
  public var flowAnalysesApi: SentryBuddyFlowAnalysesApi = DummySentryBuddyFlowAnalysesApi,
  public var healthCheckApi: SentryBuddyHealthCheckApi = DummySentryBuddyHealthCheckApi,
  public var openUrlApi: SentryBuddyOpenUrlApi = DummySentryBuddyOpenUrlApi,
  public var openFileApi: SentryBuddyOpenFileApi = DummySentryBuddyOpenFileApi,
  public var logIngestApi: SentryBuddyLogIngestApi = DummySentryBuddyLogIngestApi,
  public var sentryUiBaseUrl: String? = null,
  public var sentryUiOrganizationSlug: String? = null,
  public var sentryUiProjectId: String? = null,
  /**
   * Absolute path of the app module, injected at build time (e.g. from a `BuildConfig` field), so
   * the exception overlay can turn a stack frame into a host source path for [openFileApi].
   */
  public var sourceBasePath: String? = null,
)
