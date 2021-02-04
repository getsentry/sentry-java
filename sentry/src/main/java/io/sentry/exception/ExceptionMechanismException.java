package io.sentry.exception;

import io.sentry.protocol.Mechanism;
import io.sentry.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A throwable decorator that holds an {@link io.sentry.protocol.Mechanism} related to the decorated
 * {@link Throwable}.
 */
@ApiStatus.Internal
public final class ExceptionMechanismException extends RuntimeException {
  private static final long serialVersionUID = 142345454265713915L;

  private final @NotNull Mechanism exceptionMechanism;
  private final @NotNull Throwable throwable;
  private final @NotNull Thread thread;
  private final boolean snapshot;

  /**
   * A {@link Throwable} that decorates another with a Sentry {@link Mechanism}.
   *
   * @param mechanism The {@link Mechanism}.
   * @param throwable The {@link java.lang.Throwable}.
   * @param thread The {@link java.lang.Thread}.
   * @param snapshot if the captured {@link java.lang.Thread}'s stacktrace is a snapshot.
   */
  public ExceptionMechanismException(
      @NotNull Mechanism mechanism,
      @NotNull Throwable throwable,
      @NotNull Thread thread,
      final boolean snapshot) {
    exceptionMechanism = Objects.requireNonNull(mechanism, "Mechanism is required.");
    this.throwable = Objects.requireNonNull(throwable, "Throwable is required.");
    this.thread = Objects.requireNonNull(thread, "Thread is required.");
    this.snapshot = snapshot;
  }

  /**
   * A {@link Throwable} that decorates another with a Sentry {@link Mechanism}.
   *
   * @param mechanism The {@link Mechanism}.
   * @param throwable The {@link java.lang.Throwable}.
   * @param thread The {@link java.lang.Thread}.
   */
  public ExceptionMechanismException(
      @NotNull Mechanism mechanism, @NotNull Throwable throwable, @NotNull Thread thread) {
    this(mechanism, throwable, thread, false);
  }

  public @NotNull Mechanism getExceptionMechanism() {
    return exceptionMechanism;
  }

  public @NotNull Throwable getThrowable() {
    return throwable;
  }

  public @NotNull Thread getThread() {
    return thread;
  }

  public boolean isSnapshot() {
    return snapshot;
  }
}
