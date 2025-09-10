package io.sentry.android.distribution

/** Result of checking for updates. */
public sealed class UpdateStatus {
  /** The current app version is up to date. */
  public object UpToDate : UpdateStatus()

  /** A new release is available for download. */
  public class NewRelease(public val info: UpdateInfo) : UpdateStatus()

  /** An error occurred while checking for updates. */
  public class Error(public val message: String) : UpdateStatus()
}
