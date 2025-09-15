package io.sentry.android.distribution

/** Configuration options for Sentry Build Distribution. */
public class DistributionOptions {
  /** Organization authentication token for API access */
  public var orgAuthToken: String = ""

  /** Sentry organization slug */
  public var orgSlug: String = ""

  /** Sentry project slug */
  public var projectSlug: String = ""

  /** Base URL for Sentry API (defaults to https://sentry.io) */
  public var sentryBaseUrl: String = "https://sentry.io"

  /** Optional build configuration name for filtering (e.g., "debug", "release", "staging") */
  public var buildConfiguration: String? = null
}
