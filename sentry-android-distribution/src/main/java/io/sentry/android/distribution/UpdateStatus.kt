package io.sentry.android.distribution

/** Represents the result of checking for app updates. */
public sealed class UpdateStatus {
  /** Current app version is up to date, no update available. */
  public object UpToDate : UpdateStatus()

  /** A new release is available for download. */
  public class NewRelease(public val info: UpdateInfo) : UpdateStatus()

  /** An error occurred during the update check. */
  public class Error(public val message: String) : UpdateStatus()
}
