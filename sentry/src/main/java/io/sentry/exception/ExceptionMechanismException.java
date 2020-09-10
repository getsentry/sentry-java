package io.sentry.exception;

import io.sentry.protocol.Mechanism;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * A throwable decorator that holds an {@link io.sentry.protocol.Mechanism} related to the decorated
 * {@link Throwable}.
 */
@ApiStatus.Internal
public final class ExceptionMechanismException extends RuntimeException {
  private static final long serialVersionUID = 142345454265713915L;

  private final Mechanism exceptionMechanism;
  private final Throwable throwable;
  private final Thread thread;

  /**
   * A {@link Throwable} that decorates another with a Sentry {@link Mechanism}.
   *
   * @param mechanism The {@link Mechanism}.
   * @param throwable The {@link java.lang.Throwable}.
   * @param thread The {@link java.lang.Thread}.
   */
  public ExceptionMechanismException(
      @Nullable Mechanism mechanism, @Nullable Throwable throwable, @Nullable Thread thread) {
    this.exceptionMechanism = mechanism;
    this.throwable = throwable;
    this.thread = thread;
  }

  public Mechanism getExceptionMechanism() {
    return exceptionMechanism;
  }

  public Throwable getThrowable() {
    return throwable;
  }

  public Thread getThread() {
    return thread;
  }
}
