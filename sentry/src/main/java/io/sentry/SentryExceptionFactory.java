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
import java.util.concurrent.atomic.AtomicInteger;
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
      final @NotNull Throwable throwable) {
    final SentryStackTrace threadStacktrace = thread.getStacktrace();
    if (threadStacktrace == null) {
      return new ArrayList<>(0);
    }
    final List<SentryException> exceptions = new ArrayList<>(1);
    exceptions.add(
        getSentryException(
            throwable, mechanism, thread.getId(), threadStacktrace.getFrames(), true));
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
   * @param threadId The optional id of a {@link Thread} which the exception originated. Or null if
   *     not known.
   * @param frames stack frames that should be assigned to the stacktrace of this exception.
   * @param snapshot if the captured {@link java.lang.Thread}'s stacktrace is a snapshot, See {@link
   *     SentryStackTrace#getSnapshot()}
   */
  private @NotNull SentryException getSentryException(
      @NotNull final Throwable throwable,
      @Nullable final Mechanism exceptionMechanism,
      @Nullable final Long threadId,
      @Nullable final List<SentryStackFrame> frames,
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

    if (frames != null && !frames.isEmpty()) {
      final SentryStackTrace sentryStackTrace = new SentryStackTrace(frames);
      if (snapshot) {
        sentryStackTrace.setSnapshot(true);
      }
      exception.setStacktrace(sentryStackTrace);
    }

    exception.setThreadId(threadId);
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
    return extractExceptionQueueInternal(
        throwable, new AtomicInteger(-1), new HashSet<>(), new ArrayDeque<>(), null);
  }

  Deque<SentryException> extractExceptionQueueInternal(
      final @NotNull Throwable throwable,
      final @NotNull AtomicInteger exceptionId,
      final @NotNull HashSet<Throwable> circularityDetector,
      final @NotNull Deque<SentryException> exceptions,
      @Nullable String mechanismTypeOverride) {
    Mechanism exceptionMechanism;
    Thread thread;

    Throwable currentThrowable = throwable;
    int parentId = exceptionId.get();

    // Stack the exceptions to send them in the reverse order
    while (currentThrowable != null && circularityDetector.add(currentThrowable)) {
      boolean snapshot = false;
      final @NotNull String mechanismType =
          mechanismTypeOverride == null ? "chained" : mechanismTypeOverride;
      if (currentThrowable instanceof ExceptionMechanismException) {
        // this is for ANR I believe
        ExceptionMechanismException exceptionMechanismThrowable =
            (ExceptionMechanismException) currentThrowable;
        exceptionMechanism = exceptionMechanismThrowable.getExceptionMechanism();
        currentThrowable = exceptionMechanismThrowable.getThrowable();
        thread = exceptionMechanismThrowable.getThread();
        snapshot = exceptionMechanismThrowable.isSnapshot();
      } else {
        exceptionMechanism = new Mechanism();
        thread = Thread.currentThread();
      }

      final boolean includeSentryFrames = Boolean.FALSE.equals(exceptionMechanism.isHandled());
      final List<SentryStackFrame> frames =
          sentryStackTraceFactory.getStackFrames(
              currentThrowable.getStackTrace(), includeSentryFrames);
      SentryException exception =
          getSentryException(
              currentThrowable, exceptionMechanism, thread.getId(), frames, snapshot);
      exceptions.addFirst(exception);

      if (exceptionMechanism.getType() == null) {
        exceptionMechanism.setType(mechanismType);
      }

      if (exceptionId.get() >= 0) {
        exceptionMechanism.setParentId(parentId);
      }

      final int currentExceptionId = exceptionId.incrementAndGet();
      exceptionMechanism.setExceptionId(currentExceptionId);

      Throwable[] suppressed = currentThrowable.getSuppressed();
      if (suppressed != null && suppressed.length > 0) {
        // Disabled for now as it causes grouping in Sentry to sometimes use
        // the suppressed exception as main exception.
        // exceptionMechanism.setExceptionGroup(true);
        for (Throwable suppressedThrowable : suppressed) {
          extractExceptionQueueInternal(
              suppressedThrowable, exceptionId, circularityDetector, exceptions, "suppressed");
        }
      }
      currentThrowable = currentThrowable.getCause();
      parentId = currentExceptionId;
      mechanismTypeOverride = null;
    }

    return exceptions;
  }
}
