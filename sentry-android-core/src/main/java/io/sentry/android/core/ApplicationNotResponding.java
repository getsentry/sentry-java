// https://github.com/SalomonBrys/ANR-WatchDog/blob/1969075f75f5980e9000eaffbaa13b0daf282dcb/anr-watchdog/src/main/java/com/github/anrwatchdog/ANRError.java
// Based on the class above. The API unnecessary here was removed.
package io.sentry.android.core;

import io.sentry.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Error thrown by ANRWatchDog when an ANR is detected. Contains the stack trace of the frozen UI
 * thread.
 *
 * <p>It is important to notice that, in an ApplicationNotResponding, all the "Caused by" are not
 * really the cause of the exception. Each "Caused by" is the stack trace of a running thread. Note
 * that the main thread always comes first.
 */
final class ApplicationNotResponding extends RuntimeException {
  private static final long serialVersionUID = 252541144579117016L;

  private final @Nullable Thread thread;

  ApplicationNotResponding(final @Nullable String message) {
    super(message);
    this.thread = null;
  }

  ApplicationNotResponding(final @Nullable String message, final @NotNull Thread thread) {
    super(message);
    this.thread = Objects.requireNonNull(thread, "Thread must be provided.");
    setStackTrace(this.thread.getStackTrace());
  }

  public @Nullable Thread getThread() {
    return thread;
  }
}
