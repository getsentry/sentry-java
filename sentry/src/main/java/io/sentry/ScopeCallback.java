package io.sentry;

import org.jetbrains.annotations.NotNull;

public interface ScopeCallback {
  /**
   * Runs with the scope to read or change.
   *
   * <p>If this callback throws, the rest of it is skipped and the exception is logged at {@link
   * SentryLevel#ERROR} through {@link SentryOptions#getLogger()}, which by default prints only when
   * {@link SentryOptions#isDebug()} is enabled. When the callback was passed to a capture method,
   * the event is captured with the scope as it was before the callback ran. Fatal errors, such as
   * {@link OutOfMemoryError}, are rethrown.
   *
   * @param scope the scope
   */
  void run(@NotNull IScope scope);
}
