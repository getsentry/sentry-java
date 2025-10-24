package io.sentry.featureflags;

import io.sentry.ISentryLifecycleToken;
import io.sentry.protocol.FeatureFlag;
import io.sentry.protocol.FeatureFlags;
import io.sentry.util.AutoClosableReentrantLock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Feature flag buffer implementation optimized for usage in spans.
 *
 * <ul>
 *   <li>When full, new entries are rejected.
 *   <li>Updates to existing entries are still allowed even if full.
 *   <li>Lazily initializes its storage to optimize memory consumption.
 *   <li>Since spans are not cloned, this implementation does not need to optimize for it.
 * </ul>
 */
@ApiStatus.Internal
public final class SpanFeatureFlagBuffer implements IFeatureFlagBuffer {
  private static final int MAX_SIZE = 10;

  // lazily initializing the internal storage to reduce memory consumption when not used
  private @Nullable Map<String, Boolean> flags = null;
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  private SpanFeatureFlagBuffer() {}

  @Override
  public void add(final @Nullable String flag, final @Nullable Boolean result) {
    if (flag == null || result == null) {
      return;
    }
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (flags == null) {
        flags = new LinkedHashMap<>(MAX_SIZE);
      }

      if (flags.size() < MAX_SIZE || flags.containsKey(flag)) {
        flags.put(flag, result);
      }
    }
  }

  @Override
  public @Nullable FeatureFlags getFeatureFlags() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (flags == null || flags.isEmpty()) {
        return null;
      }

      final List<FeatureFlag> featureFlags = new ArrayList<>(flags.size());
      for (Map.Entry<String, Boolean> entry : flags.entrySet()) {
        featureFlags.add(new FeatureFlag(entry.getKey(), entry.getValue()));
      }
      return new FeatureFlags(featureFlags);
    }
  }

  @Override
  public @NotNull IFeatureFlagBuffer clone() {
    return create();
  }

  public static @NotNull IFeatureFlagBuffer create() {
    return new SpanFeatureFlagBuffer();
  }
}
