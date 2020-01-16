package io.sentry.core;

import io.sentry.core.exception.ExceptionMechanismException;
import io.sentry.core.protocol.Mechanism;
import io.sentry.core.protocol.SentryException;
import io.sentry.core.protocol.SentryStackTrace;
import io.sentry.core.util.Objects;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** class responsible for converting Java Throwable to SentryExceptions */
final class SentryExceptionFactory {

  /** the SentryStackTraceFactory */
  private final @NotNull SentryStackTraceFactory sentryStackTraceFactory;

  /**
   * ctor SentryExceptionFactory
   *
   * @param sentryStackTraceFactory the sentryStackTraceFactory
   */
  public SentryExceptionFactory(final @NotNull SentryStackTraceFactory sentryStackTraceFactory) {
    this.sentryStackTraceFactory =
        Objects.requireNonNull(sentryStackTraceFactory, "The SentryStackTraceFactory is required.");
  }

  /**
   * Creates a new instance from the given {@code throwable}.
   *
   * @param throwable the {@link Throwable} to build this instance from
   */
  @NotNull
  List<SentryException> getSentryExceptions(final @NotNull Throwable throwable) {
    return getSentryExceptions(extractExceptionQueue(throwable));
  }

  /**
   * Creates a new instance from the given {@code exceptions}.
   *
   * @param exceptions a {@link Deque} of {@link SentryException} to build this instance from
   */
  private @NotNull List<SentryException> getSentryExceptions(
      final @NotNull Deque<SentryException> exceptions) {
    return new ArrayList<>(exceptions);
  }

  /**
   * Creates a Sentry exception based on a Java Throwable.
   *
   * <p>The {@code childExceptionStackTrace} parameter is used to define the common frames with the
   * child exception (Exception caused by {@code throwable}).
   *
   * @param throwable Java exception to send to Sentry.
   * @param exceptionMechanism The optional {@link Mechanism} of the {@code throwable}. Or null if
   *     none exist.
   * @param thread The optional {@link Thread} which the exception originated. Or null if not known.
   */
  private @NotNull SentryException getSentryException(
      @NotNull final Throwable throwable,
      @Nullable final Mechanism exceptionMechanism,
      @Nullable final Thread thread) {

    final Package exceptionPackage = throwable.getClass().getPackage();
    final String fullClassName = throwable.getClass().getName();

    final SentryException exception = new SentryException();

    final String exceptionMessage = throwable.getMessage();

    final String exceptionClassName =
        exceptionPackage != null
            ? fullClassName.replace(exceptionPackage.getName() + ".", "")
            : fullClassName;

    final String exceptionPackageName =
        exceptionPackage != null ? exceptionPackage.getName() : null;

    final SentryStackTrace sentryStackTrace = new SentryStackTrace();
    sentryStackTrace.setFrames(sentryStackTraceFactory.getStackFrames(throwable.getStackTrace()));

    if (thread != null) {
      exception.setThreadId(thread.getId());
    }
    exception.setStacktrace(sentryStackTrace);
    exception.setType(exceptionClassName);
    exception.setMechanism(exceptionMechanism);
    exception.setModule(exceptionPackageName);
    exception.setValue(exceptionMessage);

    return exception;
  }

  /**
   * Transforms a {@link Throwable} into a Queue of {@link SentryException}.
   *
   * <p>Exceptions are stored in the queue from the most recent one to the oldest one.
   *
   * @param throwable throwable to transform in a queue of exceptions.
   * @return a queue of exception with StackTrace.
   */
  @TestOnly
  @NotNull
  Deque<SentryException> extractExceptionQueue(final @NotNull Throwable throwable) {
    final Deque<SentryException> exceptions = new ArrayDeque<>();
    final Set<Throwable> circularityDetector = new HashSet<>();
    Mechanism exceptionMechanism;
    Thread thread;

    Throwable currentThrowable = throwable;

    // Stack the exceptions to send them in the reverse order
    while (currentThrowable != null && circularityDetector.add(currentThrowable)) {
      if (currentThrowable instanceof ExceptionMechanismException) {
        // this is for ANR I believe
        ExceptionMechanismException exceptionMechanismThrowable =
            (ExceptionMechanismException) currentThrowable;
        exceptionMechanism = exceptionMechanismThrowable.getExceptionMechanism();
        currentThrowable = exceptionMechanismThrowable.getThrowable();
        thread = exceptionMechanismThrowable.getThread();
      } else {
        exceptionMechanism = null;
        thread = null;
      }

      SentryException exception = getSentryException(currentThrowable, exceptionMechanism, thread);
      exceptions.add(exception);
      currentThrowable = currentThrowable.getCause();
    }

    return exceptions;
  }
}
