package io.sentry;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public enum InitPriority {
  LOWEST,
  LOW,
  MEDIUM,
  HIGH,
  HIGHEST;
}
