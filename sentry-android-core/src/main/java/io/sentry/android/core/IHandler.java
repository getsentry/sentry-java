package io.sentry.android.core;

import org.jetbrains.annotations.TestOnly;

@TestOnly
interface IHandler {
  void post(Runnable runnable);

  Thread getThread();
}
