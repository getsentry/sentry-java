package io.sentry.util.runtime;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class NeutralRuntimeManager implements IRuntimeManager {
  @Override
  public <T> T runWithRelaxedPolicy(final @NotNull IRuntimeManagerCallback<T> toRun) {
    return toRun.run();
  }
}
