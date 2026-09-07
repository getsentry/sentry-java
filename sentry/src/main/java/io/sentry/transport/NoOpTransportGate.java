package io.sentry.transport;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class NoOpTransportGate implements ITransportGate {

  private static final NoOpTransportGate instance = new NoOpTransportGate();

  public static NoOpTransportGate getInstance() {
    return instance;
  }

  private NoOpTransportGate() {}

  @Override
  public boolean isConnected() {
    return true;
  }
}
