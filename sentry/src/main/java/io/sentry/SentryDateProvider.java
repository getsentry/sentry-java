package io.sentry;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface SentryDateProvider {
  SentryDate now();
}
