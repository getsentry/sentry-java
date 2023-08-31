package io.sentry.android.core;

import io.sentry.IConnectionStatusProvider;
import io.sentry.SentryOptions;
import io.sentry.transport.ITransportGate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

final class AndroidTransportGate implements ITransportGate {

  private final @NotNull SentryOptions options;

  AndroidTransportGate(final @NotNull SentryOptions options) {
    this.options = options;
  }

  @Override
  public boolean isConnected() {
    return isConnected(options.getConnectionStatusProvider().getConnectionStatus());
  }

  @TestOnly
  boolean isConnected(final @NotNull IConnectionStatusProvider.ConnectionStatus status) {
    // let's assume its connected if there's no permission or something as we can't really know
    // whether is online or not.
    switch (status) {
      case CONNECTED:
      case UNKNOWN:
      case NO_PERMISSION:
        return true;
      default:
        return false;
    }
  }
}
