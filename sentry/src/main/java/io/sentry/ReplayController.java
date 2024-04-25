package io.sentry;

import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface ReplayController {
  void start();

  void stop();

  void pause();

  void resume();

  boolean isRecording();

  void sendReplayForEvent(@NotNull SentryEvent event, @NotNull Hint hint);

  @NotNull
  SentryId getReplayId();
}
