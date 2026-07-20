package io.sentry.util;

import io.sentry.ISentryLifecycleToken;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
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
 * <p>The nested capture MUST be dropped silently — callers must not log while the guard is active.
 * The same logging integration that routes logs back into Sentry also routes the SDK's own
 * diagnostic logs, so a "dropped to prevent recursion" log line would be turned into another
 * capture, whose drop would log again, and so on. The guard suppresses the capture but not the log,
 * so logging on the drop path re-opens exactly the recursion the guard exists to break. (This only
 * bites when SDK debug logging is enabled, since {@code options.getLogger()} is otherwise a no-op,
 * but dropping silently removes the failure mode entirely.)
 *
 * <p>The guard is set ONLY around each callback's {@code execute(...)} invocation, never around the
 * whole capture pipeline, so captures made by event processors (which run outside the callback) are
 * not affected.
 *
 * <p>The guard is a depth counter rather than a boolean so that nested {@link #exit()} calls cannot
 * clear it while an outer callback is still running. Capture entry points drop while a callback is
 * active, so callbacks should never nest — but a capture path lacking an entry check must not
 * silently disarm the guard for the rest of the outer callback.
 *
 * <p>{@link #enter()} returns an {@link ISentryLifecycleToken} so callers can use
 * try-with-resources instead of a manual {@code finally exit()}. The token is a shared singleton
 * (its {@code close()} just decrements the counter), so no allocation happens per callback.
 */
@ApiStatus.Internal
public final class SentryCallbackReentrancyGuard {

  private static final ThreadLocal<Integer> depth = new ThreadLocal<>();

  private static final ISentryLifecycleToken TOKEN = SentryCallbackReentrancyGuard::exit;

  private SentryCallbackReentrancyGuard() {}

  /**
   * Whether a user callback is currently executing on this thread. When {@code true}, capture entry
   * points must drop the capture and return without logging — see the class Javadoc for why logging
   * on the drop path re-opens the recursion.
   */
  public static boolean isActive() {
    final @Nullable Integer current = depth.get();
    return current != null && current > 0;
  }

  /**
   * Marks that a user callback is starting to execute on this thread. Close the returned token (via
   * try-with-resources) once the callback returns.
   */
  public static @NotNull ISentryLifecycleToken enter() {
    final @Nullable Integer current = depth.get();
    depth.set(current == null ? 1 : current + 1);
    return TOKEN;
  }

  private static void exit() {
    final @Nullable Integer current = depth.get();
    if (current == null || current <= 1) {
      depth.remove();
    } else {
      depth.set(current - 1);
    }
  }
}
