package io.sentry.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

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
 * <p>The guard is set ONLY around each callback's {@code execute(...)} invocation, never around the
 * whole capture pipeline, so captures made by event processors (which run outside the callback) are
 * not affected.
 *
 * <p>The guard is a depth counter rather than a boolean so that nested {@link #exit()} calls cannot
 * clear it while an outer callback is still running. Capture entry points drop while a callback is
 * active, so callbacks should never nest — but a capture path lacking an entry check must not
 * silently disarm the guard for the rest of the outer callback.
 */
@ApiStatus.Internal
public final class SentryCallbackReentrancyGuard {

  private static final ThreadLocal<Integer> depth = new ThreadLocal<>();

  private SentryCallbackReentrancyGuard() {}

  /** Whether a user callback is currently executing on this thread. */
  public static boolean isActive() {
    final @Nullable Integer current = depth.get();
    return current != null && current > 0;
  }

  /** Marks that a user callback is starting to execute on this thread. */
  public static void enter() {
    final @Nullable Integer current = depth.get();
    depth.set(current == null ? 1 : current + 1);
  }

  /** Marks that the user callback finished executing on this thread. */
  public static void exit() {
    final @Nullable Integer current = depth.get();
    if (current == null || current <= 1) {
      depth.remove();
    } else {
      depth.set(current - 1);
    }
  }
}
