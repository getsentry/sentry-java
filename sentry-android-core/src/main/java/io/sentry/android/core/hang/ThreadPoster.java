package io.sentry.android.core.hang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface ThreadPoster {

  void post(@NotNull Runnable runnable);

  void remove(@NotNull Runnable runnable);
}
