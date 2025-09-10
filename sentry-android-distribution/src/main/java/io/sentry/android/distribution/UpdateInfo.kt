package io.sentry.android.distribution

/** Information about an available update. */
public data class UpdateInfo(
  /** The unique identifier for the update artifact. */
  val id: String,

  /** The version string of the update (e.g., "1.2.0"). */
  val buildVersion: String,

  /** The build number of the update. */
  val buildNumber: Int,

  /** URL for downloading the update. */
  val downloadUrl: String,

  /** Name of the application. */
  val appName: String,

  /** ISO 8601 formatted date when the build was created. */
  val createdDate: String,
)
