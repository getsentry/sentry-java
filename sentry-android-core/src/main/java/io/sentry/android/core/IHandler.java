package io.sentry.android.core;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

@TestOnly
interface IHandler {
  void post(@NotNull Runnable runnable);

  @NotNull
  Thread getThread();
}
