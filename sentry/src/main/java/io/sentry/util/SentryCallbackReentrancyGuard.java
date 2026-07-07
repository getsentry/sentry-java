package io.sentry.util;

import org.jetbrains.annotations.ApiStatus;

/**
 * Thread-local re-entrancy guard that marks whether a user-supplied {@code before*} callback
 * ({@code beforeSend}, {@code beforeBreadcrumb}, {@code beforeSendLog}, ...) is currently executing
 * on the current thread.
 *
 * <p>A callback that itself triggers another SDK capture on the same thread — directly, or
 * transitively through a logging integration that routes back into Sentry (e.g. Timber or the
 * Gradle plugin's logcat instrumentation) — would otherwise recurse indefinitely and throw {@link
 * StackOverflowError}. Capture entry points consult {@link #isActive()} and drop the nested capture
 * while a callback is running.
 *
 * <p>The flag is set ONLY around each callback's {@code execute(...)} invocation, never around the
 * whole capture pipeline, so captures made by event processors (which run outside the callback) are
 * not affected.
 */
@ApiStatus.Internal
public final class SentryCallbackReentrancyGuard {

  private static final ThreadLocal<Boolean> isRunning = new ThreadLocal<>();

  private SentryCallbackReentrancyGuard() {}

  /** Whether a user callback is currently executing on this thread. */
  public static boolean isActive() {
    return Boolean.TRUE.equals(isRunning.get());
  }

  /** Marks that a user callback is starting to execute on this thread. */
  public static void enter() {
    isRunning.set(Boolean.TRUE);
  }

  /** Marks that the user callback finished executing on this thread. */
  public static void exit() {
    isRunning.set(Boolean.FALSE);
  }
}
