package io.sentry;

import org.jetbrains.annotations.Nullable;

/** An adapter to make UncaughtExceptionHandler testable */
interface UncaughtExceptionHandler {
  Thread.UncaughtExceptionHandler getDefaultUncaughtExceptionHandler();

  void setDefaultUncaughtExceptionHandler(@Nullable Thread.UncaughtExceptionHandler handler);

  final class Adapter implements UncaughtExceptionHandler {

    static UncaughtExceptionHandler getInstance() {
      return Adapter.INSTANCE;
    }

    private static final Adapter INSTANCE = new Adapter();

    private Adapter() {}

    @Override
    public Thread.UncaughtExceptionHandler getDefaultUncaughtExceptionHandler() {
      return Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void setDefaultUncaughtExceptionHandler(
        final @Nullable Thread.UncaughtExceptionHandler handler) {
      Thread.setDefaultUncaughtExceptionHandler(handler);
    }
  }
}
