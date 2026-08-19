package io.sentry.android.buddy

import io.sentry.android.buddy.bridge.DummySentryBuddyFlowAnalysesApi
import io.sentry.android.buddy.bridge.DummySentryBuddyHealthCheckApi
import io.sentry.android.buddy.bridge.DummySentryBuddyOpenUrlApi
import io.sentry.android.buddy.bridge.SentryBuddyFlowAnalysesApi
import io.sentry.android.buddy.bridge.SentryBuddyHealthCheckApi
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
  public var sentryUiBaseUrl: String? = null,
  public var sentryUiOrganizationSlug: String? = null,
  public var sentryUiProjectId: String? = null,
)
