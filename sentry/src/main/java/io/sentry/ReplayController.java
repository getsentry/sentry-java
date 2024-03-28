package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface ReplayController {
  void start();

  void stop();

  void pause();

  void resume();

  void sendReplayForEvent(@NotNull SentryEvent event);
}
