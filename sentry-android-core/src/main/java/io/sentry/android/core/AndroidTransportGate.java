package io.sentry.android.core;

import android.content.Context;
import io.sentry.ILogger;
import io.sentry.android.core.util.ConnectivityChecker;
import io.sentry.transport.ITransportGate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

final class AndroidTransportGate implements ITransportGate {

  private final Context context;
  private final @NotNull ILogger logger;

  AndroidTransportGate(final @NotNull Context context, final @NotNull ILogger logger) {
    this.context = context;
    this.logger = logger;
  }

  @Override
  public boolean isConnected() {
    return isConnected(ConnectivityChecker.getConnectionStatus(context, logger));
  }

  @TestOnly
  boolean isConnected(final @NotNull ConnectivityChecker.Status status) {
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
