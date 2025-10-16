package io.sentry.util.runtime;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface IRuntimeManager {
  <T> T runWithRelaxedPolicy(final @NotNull IRuntimeManagerCallback<T> toRun);

  interface IRuntimeManagerCallback<T> {
    T run();
  }
}
