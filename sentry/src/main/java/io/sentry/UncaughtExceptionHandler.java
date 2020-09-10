package io.sentry;

/** An adapter to make UncaughtExceptionHandler testable */
interface UncaughtExceptionHandler {
  Thread.UncaughtExceptionHandler getDefaultUncaughtExceptionHandler();

  void setDefaultUncaughtExceptionHandler(Thread.UncaughtExceptionHandler handler);

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
    public void setDefaultUncaughtExceptionHandler(Thread.UncaughtExceptionHandler handler) {
      Thread.setDefaultUncaughtExceptionHandler(handler);
    }
  }
}
