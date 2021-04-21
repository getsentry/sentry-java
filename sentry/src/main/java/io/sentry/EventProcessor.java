package io.sentry;

import io.sentry.protocol.SentryTransaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface EventProcessor {
  @Nullable
  SentryEvent process(@NotNull SentryEvent event, @Nullable Object hint);

  @Nullable
  default SentryTransaction process(@NotNull SentryTransaction transaction, @Nullable Object hint) {
    return transaction;
  }
}
