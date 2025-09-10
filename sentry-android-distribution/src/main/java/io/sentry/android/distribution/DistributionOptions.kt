package io.sentry.android.distribution

/** Configuration options for build distribution functionality. */
public data class DistributionOptions(
  /**
   * Organization auth token for authenticating with Sentry API. This will be used to authenticate
   * requests to the preprod artifacts endpoint.
   */
  val orgAuthToken: String,

  /** The organization slug in Sentry where preprod artifacts are stored. */
  val organizationSlug: String,

  /** The project slug in Sentry where preprod artifacts are stored. */
  val projectSlug: String,

  /**
   * Optional build configuration name to filter builds. If not provided, all builds will be
   * considered.
   */
  val buildConfiguration: String? = null,

  /** Base URL for Sentry instance. Defaults to sentry.io. */
  val sentryBaseUrl: String = "https://sentry.io",
)
