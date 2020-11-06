package io.sentry;

import io.sentry.util.Objects;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Arbitrary data used in {@link SamplingContext} to determine if transaction is going to be
 * sampled.
 */
public final class CustomSamplingContext {
  private final @NotNull Map<String, Object> data = new HashMap<>();

  public void put(final @NotNull String key, final @Nullable Object value) {
    Objects.requireNonNull(key, "key is required");
    this.data.put(key, value);
  }

  public Object get(final @NotNull String key) {
    Objects.requireNonNull(key, "key is required");
    return this.data.get(key);
  }

  public Map<String, Object> getData() {
    return data;
  }
}
