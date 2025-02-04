package io.sentry.backpressure;

public final class NoOpBackpressureMonitor implements IBackpressureMonitor {

  private static final NoOpBackpressureMonitor instance = new NoOpBackpressureMonitor();

  private NoOpBackpressureMonitor() {}

  public static NoOpBackpressureMonitor getInstance() {
    return instance;
  }

  @Override
  public void start() {
    // do nothing
  }

  @Override
  public int getDownsampleFactor() {
    return 0;
  }

  @Override
  public void close() {
    // do nothing
  }
}
