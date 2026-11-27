package io.sentry.android.core;

import android.os.Handler;
import android.os.Looper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class MainLooperHandler {
  private final @NotNull Handler handler;

  public MainLooperHandler() {
    this(Looper.getMainLooper());
  }

  public MainLooperHandler(final @NotNull Looper looper) {
    handler = new Handler(looper);
  }

  public void post(final @NotNull Runnable runnable) {
    handler.post(runnable);
  }

  public @NotNull Thread getThread() {
    return handler.getLooper().getThread();
  }
}
