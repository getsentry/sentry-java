package io.sentry.android.core.hang;

import android.os.Handler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class HandlerThreadPoster implements ThreadPoster {

  private final @NotNull Handler handler;

  public HandlerThreadPoster(final @NotNull Handler handler) {
    this.handler = handler;
  }

  @Override
  public void post(final @NotNull Runnable runnable) {
    handler.post(runnable);
  }

  @Override
  public void remove(final @NotNull Runnable runnable) {
    handler.removeCallbacks(runnable);
  }
}
