package io.sentry;

import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.NotNull;

public final class NoOpReplayController implements ReplayController {

  private static final NoOpReplayController instance = new NoOpReplayController();

  public static NoOpReplayController getInstance() {
    return instance;
  }

  private NoOpReplayController() {}

  @Override
  public void start() {}

  @Override
  public void stop() {}

  @Override
  public void pause() {}

  @Override
  public void resume() {}

  @Override
  public boolean isRecording() {
    return false;
  }

  @Override
  public void sendReplayForEvent(@NotNull SentryEvent event, @NotNull Hint hint) {}

  @Override
  public @NotNull SentryId getReplayId() {
    return SentryId.EMPTY_ID;
  }
}
