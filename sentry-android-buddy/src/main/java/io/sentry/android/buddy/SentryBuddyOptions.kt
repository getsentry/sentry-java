package io.sentry.android.buddy

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public class SentryBuddyOptions
@JvmOverloads
public constructor(
  public var enabled: Boolean = true,
  public var showOverlay: Boolean = true,
  public var flowAnalysesApi: SentryBuddyFlowAnalysesApi = DummySentryBuddyFlowAnalysesApi,
  public var openUrlApi: SentryBuddyOpenUrlApi = DummySentryBuddyOpenUrlApi,
  public var sentryUiBaseUrl: String? = null,
  public var sentryUiOrganizationSlug: String? = null,
  public var sentryUiProjectId: String? = null,
)
