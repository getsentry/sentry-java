package io.sentry.android.core;

import android.os.Debug;
import io.sentry.IDebugManager;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class AndroidDebugManager implements IDebugManager {

  @Override
  public boolean isDebuggerConnected() {
    return Debug.isDebuggerConnected() || Debug.waitingForDebugger();
  }
}
