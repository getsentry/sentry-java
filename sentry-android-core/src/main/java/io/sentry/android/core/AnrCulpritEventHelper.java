package io.sentry.android.core;

import io.sentry.SentryEvent;
import io.sentry.SentryExceptionFactory;
import io.sentry.android.core.anr.AggregatedStackTrace;
import io.sentry.android.core.anr.AnrCulpritIdentifier;
import io.sentry.exception.ExceptionMechanismException;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Turns the culprit of an ANR profile into event exceptions, for fatal and non-fatal ANRs. */
@ApiStatus.Internal
public final class AnrCulpritEventHelper {

  private final @NotNull SentryExceptionFactory exceptionFactory;

  public AnrCulpritEventHelper(final @NotNull SentryExceptionFactory exceptionFactory) {
    this.exceptionFactory = exceptionFactory;
  }

  /**
   * The exception message is the class and method of the top frame of the culprit stack.
   *
   * @return the exceptions, or {@code null} if the culprit stack is empty
   */
  public @Nullable List<SentryException> createExceptions(
      final @NotNull AggregatedStackTrace culprit, final @NotNull Mechanism mechanism) {
    final @NotNull StackTraceElement[] stack = culprit.getStack();
    if (stack.length == 0) {
      return null;
    }
    final @NotNull StackTraceElement top = stack[0];
    final @NotNull ApplicationNotResponding exception =
        new ApplicationNotResponding(top.getClassName() + "." + top.getMethodName());
    exception.setStackTrace(stack);

    return exceptionFactory.getSentryExceptions(
        new ExceptionMechanismException(mechanism, exception, null, false));
  }

  /** Returns {@code true} if the event has exceptions and none of their frames is an app frame. */
  public static boolean hasOnlySystemFrames(final @NotNull SentryEvent event) {
    final @Nullable List<SentryException> exceptions = event.getExceptions();
    if (exceptions == null || exceptions.isEmpty()) {
      return false;
    }

    for (final @NotNull SentryException exception : exceptions) {
      final @Nullable SentryStackTrace stacktrace = exception.getStacktrace();
      if (stacktrace != null) {
        final @Nullable List<SentryStackFrame> frames = stacktrace.getFrames();
        if (frames != null && !frames.isEmpty()) {
          for (final @NotNull SentryStackFrame frame : frames) {
            if (frame.isInApp() != null && frame.isInApp()) {
              return false;
            }
            final @Nullable String module = frame.getModule();
            if (module != null && !AnrCulpritIdentifier.isSystemFrame(module)) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }
}
