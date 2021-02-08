package io.sentry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface EventProcessor {
  @Nullable
  SentryEvent process(@NotNull SentryEvent event, @Nullable Object hint);
}
