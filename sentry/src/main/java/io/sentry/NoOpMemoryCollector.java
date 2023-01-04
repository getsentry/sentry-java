package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NoOpMemoryCollector implements IMemoryCollector {

  private static final NoOpMemoryCollector instance = new NoOpMemoryCollector();

  public static NoOpMemoryCollector getInstance() {
    return instance;
  }

  private NoOpMemoryCollector() {}

  @Override
  public @Nullable MemoryCollectionData collect() {
    return null;
  }
}
