package io.sentry.core;

import io.sentry.core.exception.ExceptionMechanismThrowable;
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
import org.jetbrains.annotations.TestOnly;

/** class responsible for converting Java Throwable to SentryExceptions */
final class SentryExceptionFactory {

  private final SentryStackTraceFactory sentryStackTraceFactory;

  public SentryExceptionFactory(SentryStackTraceFactory sentryStackTraceFactory) {
    this.sentryStackTraceFactory =
        Objects.requireNonNull(sentryStackTraceFactory, "The SentryStackTraceFactory is required.");
  }

  /**
   * Creates a new instance from the given {@code throwable}.
   *
   * @param throwable the {@link Throwable} to build this instance from
   */
  List<SentryException> getSentryExceptions(final Throwable throwable) {
    return getSentryExceptions(extractExceptionQueue(throwable));
  }

  /**
   * Creates a new instance from the given {@code exceptions}.
   *
   * @param exceptions a {@link Deque} of {@link SentryException} to build this instance from
   */
  private List<SentryException> getSentryExceptions(final Deque<SentryException> exceptions) {
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
   */
  private SentryException getSentryException(
      final Throwable throwable, final Mechanism exceptionMechanism) {

    Package exceptionPackage = throwable.getClass().getPackage();
    String fullClassName = throwable.getClass().getName();

    SentryException exception = new SentryException();

    String exceptionMessage = throwable.getMessage();

    String exceptionClassName =
        exceptionPackage != null
            ? fullClassName.replace(exceptionPackage.getName() + ".", "")
            : fullClassName;

    String exceptionPackageName = exceptionPackage != null ? exceptionPackage.getName() : null;

    SentryStackTrace sentryStackTrace = new SentryStackTrace();
    sentryStackTrace.setFrames(sentryStackTraceFactory.getStackFrames(throwable.getStackTrace()));

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
  Deque<SentryException> extractExceptionQueue(final Throwable throwable) {
    Deque<SentryException> exceptions = new ArrayDeque<>();
    Set<Throwable> circularityDetector = new HashSet<>();
    Mechanism exceptionMechanism;

    Throwable currentThrowable = throwable;

    // Stack the exceptions to send them in the reverse order
    while (currentThrowable != null && circularityDetector.add(currentThrowable)) {
      if (currentThrowable instanceof ExceptionMechanismThrowable) {
        // this is for ANR I believe
        ExceptionMechanismThrowable exceptionMechanismThrowable =
            (ExceptionMechanismThrowable) currentThrowable;
        exceptionMechanism = exceptionMechanismThrowable.getExceptionMechanism();
        currentThrowable = exceptionMechanismThrowable.getThrowable();
      } else {
        exceptionMechanism = null;
      }

      SentryException exception = getSentryException(currentThrowable, exceptionMechanism);
      exceptions.add(exception);
      currentThrowable = currentThrowable.getCause();
    }

    return exceptions;
  }
}
