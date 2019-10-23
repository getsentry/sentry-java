package io.sentry.core;

/** An adapter to make UncaughtExceptionHandler testable */
interface UncaughtExceptionHandler {
  Thread.UncaughtExceptionHandler getDefaultUncaughtExceptionHandler();

  void setDefaultUncaughtExceptionHandler(Thread.UncaughtExceptionHandler handler);

  class Adapter implements UncaughtExceptionHandler {

    UncaughtExceptionHandler getInstance() {
      return Adapter.INSTANCE;
    }

    static final Adapter INSTANCE = new Adapter();

    private Adapter() {}

    @Override
    public Thread.UncaughtExceptionHandler getDefaultUncaughtExceptionHandler() {
      return Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void setDefaultUncaughtExceptionHandler(Thread.UncaughtExceptionHandler handler) {
      Thread.setDefaultUncaughtExceptionHandler(handler);
    }
  }
}
