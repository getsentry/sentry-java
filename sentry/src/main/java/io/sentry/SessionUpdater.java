package io.sentry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

interface SessionUpdater {
  @Nullable
  Session updateSessionData(
      final @NotNull SentryEvent event, final @Nullable Object hint, final @Nullable Scope scope);
}
