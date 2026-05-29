package io.sentry.android.core;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the {@link FirstUiLoadListener} wired by {@link AppStartIntegration} for {@link
 * ActivityLifecycleIntegration}. One instance is created per SDK init in {@link
 * AndroidOptionsInitializer} and shared by both integrations.
 */
@ApiStatus.Internal
final class FirstUiLoadCoordinator {

  private volatile @Nullable FirstUiLoadListener listener;

  void setListener(final @Nullable FirstUiLoadListener listener) {
    this.listener = listener;
  }

  @Nullable
  FirstUiLoadListener getListener() {
    return listener;
  }
}
