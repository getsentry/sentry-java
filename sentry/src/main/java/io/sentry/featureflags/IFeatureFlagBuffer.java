package io.sentry.featureflags;

import io.sentry.protocol.FeatureFlags;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface IFeatureFlagBuffer {
  void add(final @Nullable String flag, final @Nullable Boolean result);

  @Nullable
  FeatureFlags getFeatureFlags();

  @NotNull
  IFeatureFlagBuffer clone();
}
