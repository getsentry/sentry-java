package io.sentry.featureflags;

import io.sentry.protocol.FeatureFlags;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NoOpFeatureFlagBuffer implements IFeatureFlagBuffer {
  private static final NoOpFeatureFlagBuffer instance = new NoOpFeatureFlagBuffer();

  public static NoOpFeatureFlagBuffer getInstance() {
    return instance;
  }

  @Override
  public void add(final @Nullable String flag, final @Nullable Boolean result) {}

  @Override
  public @Nullable FeatureFlags getFeatureFlags() {
    return null;
  }

  @Override
  public @NotNull IFeatureFlagBuffer clone() {
    return instance;
  }
}
