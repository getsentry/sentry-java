package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class JavaMemoryCollector implements IMemoryCollector {

  private final @NotNull Runtime runtime = Runtime.getRuntime();

  @Override
  public @Nullable MemoryCollectionData collect() {
    final long now = System.currentTimeMillis();
    final long usedMemory = runtime.totalMemory() - runtime.freeMemory();
    return new MemoryCollectionData(now, usedMemory);
  }
}
