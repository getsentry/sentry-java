package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface IConnectionStatusProvider {

  enum ConnectionStatus {
    UNKNOWN,
    CONNECTED,
    DISCONNECTED,
    NO_PERMISSION
  }

  interface IConnectionStatusObserver {
    void onConnectionStatusChanged(ConnectionStatus status);
  }

  @NotNull
  ConnectionStatus getConnectionStatus();

  @Nullable
  String getConnectionType();

  void addConnectionStatusObserver(@NotNull final IConnectionStatusObserver observer);

  void removeConnectionStatusObserver(@NotNull final IConnectionStatusObserver observer);
}
