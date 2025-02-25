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

  void captureReplay(@Nullable Boolean isTerminating);

  @NotNull
  SentryId getReplayId();

  void setBreadcrumbConverter(@NotNull ReplayBreadcrumbConverter converter);

  @NotNull
  ReplayBreadcrumbConverter getBreadcrumbConverter();
}
