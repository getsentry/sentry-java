package io.sentry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

interface SessionUpdater {
  /**
   * Called when an event was triggered potentially during active session.
   *
   * @param event - the sentry event
   * @param hint - the hint sent with event
   * @param scope - current scope
   * @return the active session, or null if there was no active session
   */
  @Nullable
  Session updateSessionData(
      final @NotNull SentryEvent event, final @NotNull Hint hint, final @Nullable Scope scope);
}
