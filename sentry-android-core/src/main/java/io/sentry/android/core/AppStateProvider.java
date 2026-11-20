package io.sentry.android.core;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Tells listeners whether the app is in the foreground. Implementations may call the listeners on
 * any thread.
 */
@ApiStatus.Internal
public interface AppStateProvider {

  /** Calls the new listener right away with the current app state, if it is known. */
  void addAppStateListener(@NotNull AppState.AppStateListener listener);

  void removeAppStateListener(@NotNull AppState.AppStateListener listener);
}
