package io.sentry.android.distribution

/** Configuration options for Sentry Build Distribution. */
public class DistributionOptions(
  /** Organization authentication token for API access */
  public val orgAuthToken: String,
  /** Sentry organization slug */
  public val organizationSlug: String,
  /** Sentry project slug */
  public val projectSlug: String,
  /** Base URL for Sentry API (defaults to https://sentry.io) */
  public val sentryBaseUrl: String = "https://sentry.io",
  /** Optional build configuration name for filtering */
  public val buildConfiguration: String? = null,
)
