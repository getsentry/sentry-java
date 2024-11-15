package io.sentry.android.core.unity;

import io.sentry.exception.ExceptionMechanismException;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import io.sentry.util.Objects;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
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
public final class SentryUnityExceptionFactory {

  /** the SentryStackTraceFactory */
  private final @NotNull SentryUnityStackTraceFactory sentryStackTraceFactory;
  private final UnityErrorParser unityErrorParser = new UnityErrorParser();

  /**
   * ctor SentryExceptionFactory
   *
   * @param sentryStackTraceFactory the sentryStackTraceFactory
   */
  public SentryUnityExceptionFactory(final @NotNull SentryUnityStackTraceFactory sentryStackTraceFactory) {
    this.sentryStackTraceFactory =
      Objects.requireNonNull(sentryStackTraceFactory, "The SentryStackTraceFactory is required.");
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
    final List<SentryException> exceptionsList = new ArrayList<>(exceptions);
    Collections.reverse(exceptionsList);
    return exceptionsList;
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
   * @param snapshot if the captured {@link Thread}'s stacktrace is a snapshot, See {@link
   *     SentryStackTrace#getSnapshot()}
   */
  private @NotNull SentryException getSentryException(
    @NotNull final Throwable throwable,
    @Nullable final Mechanism exceptionMechanism,
    @Nullable final Long threadId,
    @Nullable final List<SentryStackFrame> frames,
    final boolean snapshot) {

    final SentryException exception = new SentryException();

    final String exceptionMessage = throwable.getMessage();
    if (exceptionMessage != null && exceptionMessage.startsWith("***")) {
      // we're parsing a unity exception that is wrapped into a java.lang.Error
      try (final BufferedReader reader =
             new BufferedReader(new StringReader(exceptionMessage))) {
        final Mechanism nativeMechanism = new Mechanism();
        nativeMechanism.setSynthetic(true);
        nativeMechanism.setHandled(false);
        nativeMechanism.setType("signalhandler");
        exception.setMechanism(nativeMechanism);
        final Lines lines = Lines.readLines(reader);
        unityErrorParser.parse(exception, lines);
      } catch (Throwable e) {
        // ignore
      }
    } else {
      //final Package exceptionPackage = throwable.getClass().getPackage();
      //final String fullClassName = throwable.getClass().getName();
      //
      //final String exceptionClassName =
      //  exceptionPackage != null
      //    ? fullClassName.replace(exceptionPackage.getName() + ".", "")
      //    : fullClassName;
      //
      //final String exceptionPackageName =
      //  exceptionPackage != null ? exceptionPackage.getName() : null;
      //
      //exception.setThreadId(threadId);
      //exception.setType(exceptionClassName);
      //exception.setMechanism(exceptionMechanism);
      //exception.setModule(exceptionPackageName);
      //exception.setValue(exceptionMessage);
    }

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

      final List<SentryStackFrame> frames =
        sentryStackTraceFactory.getStackFrames(currentThrowable.getStackTrace());
      SentryException exception =
        getSentryException(
          currentThrowable, exceptionMechanism, thread.getId(), frames, snapshot);
      if (exception.getType() != null) {
        exceptions.addFirst(exception);
      }
      currentThrowable = currentThrowable.getCause();
    }

    return exceptions;
  }
}
