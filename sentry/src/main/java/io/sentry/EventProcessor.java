package io.sentry;

import org.jetbrains.annotations.Nullable;

public interface EventProcessor {
  @Nullable
  SentryEvent process(SentryEvent event, @Nullable Object hint);
}
