package io.sentry;

import java.io.Closeable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface IConnectionStatusProvider extends Closeable {

  enum ConnectionStatus {
    UNKNOWN,
    CONNECTED,
    DISCONNECTED,
    NO_PERMISSION
  }

  interface IConnectionStatusObserver {
    /**
     * Invoked whenever the connection status changed.
     *
     * @param status the new connection status
     */
    void onConnectionStatusChanged(@NotNull ConnectionStatus status);
  }

  /**
   * Gets the connection status.
   *
   * @return the current connection status
   */
  @NotNull
  ConnectionStatus getConnectionStatus();

  /**
   * Gets the connection type.
   *
   * @return the current connection type. E.g. "ethernet", "wifi" or "cellular"
   */
  @Nullable
  String getConnectionType();

  /**
   * Adds an observer for listening to connection status changes.
   *
   * @param observer the observer to register
   * @return true if the observer was sucessfully registered
   */
  boolean addConnectionStatusObserver(@NotNull final IConnectionStatusObserver observer);

  /**
   * Removes an observer.
   *
   * @param observer a previously added observer via {@link
   *     #addConnectionStatusObserver(IConnectionStatusObserver)}
   */
  void removeConnectionStatusObserver(@NotNull final IConnectionStatusObserver observer);
}
