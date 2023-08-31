package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NoOpConnectionStatusProvider implements IConnectionStatusProvider {
  @Override
  public @NotNull ConnectionStatus getConnectionStatus() {
    return ConnectionStatus.UNKNOWN;
  }

  @Override
  public @Nullable String getConnectionType() {
    return null;
  }

  @Override
  public void addConnectionStatusObserver(@NotNull IConnectionStatusObserver observer) {
    // no-op
  }

  @Override
  public void removeConnectionStatusObserver(@NotNull IConnectionStatusObserver observer) {
    // no-op
  }
}
