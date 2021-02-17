package io.sentry.android.core;

import android.os.Handler;
import android.os.Looper;
import org.jetbrains.annotations.NotNull;

final class MainLooperHandler implements IHandler {
  private final @NotNull Handler handler;

  MainLooperHandler() {
    this(Looper.getMainLooper());
  }

  MainLooperHandler(final @NotNull Looper looper) {
    handler = new Handler(looper);
  }

  @Override
  public void post(final @NotNull Runnable runnable) {
    handler.post(runnable);
  }

  @Override
  public @NotNull Thread getThread() {
    return handler.getLooper().getThread();
  }
}
