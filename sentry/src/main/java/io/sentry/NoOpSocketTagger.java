package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class NoOpSocketTagger implements ISocketTagger {

  private static final NoOpSocketTagger instance = new NoOpSocketTagger();

  private NoOpSocketTagger() {}

  public static @NotNull ISocketTagger getInstance() {
    return instance;
  }

  @Override
  public void tagSockets() {
    // No operation
  }

  @Override
  public void untagSockets() {
    // No operation
  }
}
