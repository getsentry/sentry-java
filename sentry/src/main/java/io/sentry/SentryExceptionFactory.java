package io.sentry;

import io.sentry.exception.ExceptionMechanismException;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import io.sentry.protocol.SentryThread;
import io.sentry.util.Objects;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** class responsible for converting Java Throwable to SentryExceptions */
@ApiStatus.Internal
public final class SentryExceptionFactory {

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

  @NotNull
  public List<SentryException> getSentryExceptionsFromThread(
    final @NotNull SentryThread thread,
    final @NotNull Mechanism mechanism,
    final boolean snapshot
  ) {
    final ArrayList<SentryException> exceptions = new ArrayList<>();
    final SentryException exception = new SentryException();

    final SentryStackTrace threadStacktrace = thread.getStacktrace();
    if (threadStacktrace != null) {
      final SentryStackTrace stacktrace = new SentryStackTrace(threadStacktrace.getFrames());
      if (snapshot) {
        stacktrace.setSnapshot(true);
      }
      exception.setStacktrace(stacktrace);
    }
    exception.setThreadId(thread.getId());
    exception.setMechanism(mechanism);

    exceptions.add(exception);
    return exceptions;
  }

  /**
   * Creates a new instance from the given {@code throwable}.
   *
   * @param throwable the {@link Throwable} to build this instance from
   */
  @NotNull
  public List<SentryException> getSentryExceptions(final @NotNull Throwable throwable) {
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
   * @param snapshot if the captured {@link java.lang.Thread}'s stacktrace is a snapshot, See {@link
   *     SentryStackTrace#getSnapshot()}
   */
  private @NotNull SentryException getSentryException(
      @NotNull final Throwable throwable,
      @Nullable final Mechanism exceptionMechanism,
      @Nullable final Thread thread,
      final boolean snapshot) {

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

    final List<SentryStackFrame> frames =
        sentryStackTraceFactory.getStackFrames(throwable.getStackTrace());
    if (frames != null && !frames.isEmpty()) {
      final SentryStackTrace sentryStackTrace = new SentryStackTrace(frames);
      if (snapshot) {
        sentryStackTrace.setSnapshot(true);
      }
      exception.setStacktrace(sentryStackTrace);
    }

    if (thread != null) {
      exception.setThreadId(thread.getId());
    }
    exception.setType(exceptionClassName);
    exception.setMechanism(exceptionMechanism);
    exception.setModule(exceptionPackageName);
    exception.setValue(exceptionMessage);

    return exception;
  }

  /**
   * Transforms a {@link Throwable} into a Queue of {@link SentryException}.
   *
   * <p>Multiple values represent chained exceptions and should be sorted oldest to newest.
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
      boolean snapshot = false;
      if (currentThrowable instanceof ExceptionMechanismException) {
        // this is for ANR I believe
        ExceptionMechanismException exceptionMechanismThrowable =
            (ExceptionMechanismException) currentThrowable;
        exceptionMechanism = exceptionMechanismThrowable.getExceptionMechanism();
        currentThrowable = exceptionMechanismThrowable.getThrowable();
        thread = exceptionMechanismThrowable.getThread();
        snapshot = exceptionMechanismThrowable.isSnapshot();
      } else {
        exceptionMechanism = null;
        thread = Thread.currentThread();
      }

      SentryException exception =
          getSentryException(currentThrowable, exceptionMechanism, thread, snapshot);
      exceptions.addFirst(exception);
      currentThrowable = currentThrowable.getCause();
    }

    return exceptions;
  }
}
