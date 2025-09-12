package io.sentry.android.distribution

/**
 * Information about an available app update.
 *
 * @param id Unique identifier for this build artifact
 * @param buildVersion Version string (e.g., "1.2.0")
 * @param buildNumber Build number for this version
 * @param downloadUrl URL where the update can be downloaded
 * @param appName Application name
 * @param createdDate ISO timestamp when this build was created
 */
public data class UpdateInfo(
  val id: String,
  val buildVersion: String,
  val buildNumber: Int,
  val downloadUrl: String,
  val appName: String,
  val createdDate: String,
)
