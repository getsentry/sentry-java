package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Represents the result of checking for app updates. */
@ApiStatus.Experimental
public abstract class UpdateStatus {

  /** Current app version is up to date, no update available. */
  public static final class UpToDate extends UpdateStatus {
    private static final UpToDate INSTANCE = new UpToDate();

    private UpToDate() {}

    public static UpToDate getInstance() {
      return INSTANCE;
    }
  }

  /** A new release is available for download. */
  public static final class NewRelease extends UpdateStatus {
    private final @NotNull UpdateInfo info;

    public NewRelease(final @NotNull UpdateInfo info) {
      this.info = info;
    }

    public @NotNull UpdateInfo getInfo() {
      return info;
    }
  }

  /** An error occurred during the update check. */
  public static final class UpdateError extends UpdateStatus {
    private final @NotNull String message;

    public UpdateError(final @NotNull String message) {
      this.message = message;
    }

    public @NotNull String getMessage() {
      return message;
    }
  }
}
