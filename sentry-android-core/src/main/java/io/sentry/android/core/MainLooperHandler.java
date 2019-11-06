package io.sentry.android.core;

import android.os.Handler;
import android.os.Looper;

final class MainLooperHandler implements IHandler {
  private final Handler handler;

  MainLooperHandler() {
    handler = new Handler(Looper.getMainLooper());
  }

  @Override
  public void post(Runnable runnable) {
    handler.post(runnable);
  }

  @Override
  public Thread getThread() {
    return handler.getLooper().getThread();
  }
}
