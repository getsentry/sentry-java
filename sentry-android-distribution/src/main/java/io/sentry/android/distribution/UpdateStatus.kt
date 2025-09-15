package io.sentry.android.distribution

/** Represents the result of checking for app updates. */
public sealed class UpdateStatus {
  /** Current app version is up to date, no update available. */
  public object UpToDate : UpdateStatus()

  /** A new release is available for download. */
  public data class NewRelease(val info: UpdateInfo) : UpdateStatus()

  /** An error occurred during the update check. */
  public data class Error(val message: String) : UpdateStatus()
}
