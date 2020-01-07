package io.sentry.android.core;

import android.content.Context;
import io.sentry.android.core.util.ConnectivityChecker;
import io.sentry.core.ILogger;
import io.sentry.core.transport.ITransportGate;
import org.jetbrains.annotations.NotNull;

final class AndroidTransportGate implements ITransportGate {

  private final Context context;
  private final ILogger loger;

  AndroidTransportGate(@NotNull Context context, @NotNull ILogger loger) {
    this.context = context;
    this.loger = loger;
  }

  @Override
  public boolean isSendingAllowed() {
    Boolean connected = ConnectivityChecker.isConnected(context, loger);
    // let's assume its connected if there's no permission or something as we can't really know
    // whether is online or not.
    return connected != null ? connected : true;
  }
}
