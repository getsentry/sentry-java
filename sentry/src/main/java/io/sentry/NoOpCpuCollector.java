package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NoOpCpuCollector implements ICpuCollector {

  private static final NoOpCpuCollector instance = new NoOpCpuCollector();

  public static NoOpCpuCollector getInstance() {
    return instance;
  }

  private NoOpCpuCollector() {}

  @Override
  public void setup() {}

  @Override
  public @Nullable CpuCollectionData collect() {
    return null;
  }
}
