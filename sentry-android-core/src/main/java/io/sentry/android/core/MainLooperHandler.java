package io.sentry.android.core;

import android.os.Handler;
import android.os.Looper;
import org.jetbrains.annotations.NotNull;

final class MainLooperHandler {
  private final @NotNull Handler handler;

  MainLooperHandler() {
    this(Looper.getMainLooper());
  }

  MainLooperHandler(final @NotNull Looper looper) {
    handler = new Handler(looper);
  }

  public void post(final @NotNull Runnable runnable) {
    handler.post(runnable);
  }

  public @NotNull Thread getThread() {
    return handler.getLooper().getThread();
  }
}
