package io.sentry.util.runtime;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class NeutralRuntimeManager implements IRuntimeManager {
  @Override
  public <T> T runWithRelaxedPolicy(final @NotNull IRuntimeManagerCallback<T> toRun) {
    return toRun.run();
  }

  @Override
  public void runWithRelaxedPolicy(final @NotNull Runnable toRun) {
    toRun.run();
  }
}
