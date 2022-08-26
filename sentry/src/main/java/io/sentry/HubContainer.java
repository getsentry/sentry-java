package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class HubContainer {

  private @Nullable IHub hub;

  public HubContainer(@NotNull IHub hub) {
    this.hub = hub;
  }

  public @Nullable IHub getHub() {
    return hub;
  }

  public void unset() {
    hub = null;
  }
}
