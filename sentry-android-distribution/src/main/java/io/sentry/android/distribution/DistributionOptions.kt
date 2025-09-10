package io.sentry.android.distribution

/**
 * Configuration options for Sentry Build Distribution.
 *
 * @param orgAuthToken Organization authentication token for API access
 * @param organizationSlug Sentry organization slug
 * @param projectSlug Sentry project slug
 * @param sentryBaseUrl Base URL for Sentry API (defaults to https://sentry.io)
 * @param buildConfiguration Optional build configuration name for filtering
 */
public data class DistributionOptions(
  val orgAuthToken: String,
  val organizationSlug: String,
  val projectSlug: String,
  val sentryBaseUrl: String = "https://sentry.io",
  val buildConfiguration: String? = null,
)
