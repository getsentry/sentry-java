package io.sentry;

import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface ReplayController {
  void start();

  void stop();

  void pause();

  void resume();

  boolean isRecording();

  void sendReplayForEvent(@NotNull SentryEvent event, @NotNull Hint hint);

  void sendReplay(@Nullable Boolean isCrashed, @Nullable String eventId, @Nullable Hint hint);

  @NotNull
  SentryId getReplayId();
}
