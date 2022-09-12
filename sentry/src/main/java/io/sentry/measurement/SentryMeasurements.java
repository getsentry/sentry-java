package io.sentry.measurement;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryMeasurements {
  private final Map<String, Double> internalStorage = new ConcurrentHashMap<>();

  public void set(final @NotNull String key, final @Nullable Double value) {
    internalStorage.put(key, value);
  }

  public @Nullable Double get(final @NotNull String key) {
    return internalStorage.get(key);
  }
}
