package io.sentry;

import org.jetbrains.annotations.ApiStatus;

@HybridSdkApi
@ApiStatus.Internal
public interface SentryDateProvider {
  SentryDate now();
}
