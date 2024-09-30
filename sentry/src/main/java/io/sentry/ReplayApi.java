package io.sentry;

import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.NotNull;

public final class ReplayApi {
  private final @NotNull ReplayController replayController;

  public ReplayApi(final @NotNull ReplayController replayController) {
    this.replayController = replayController;
  }

  /** Resumes screen recording if it was paused. */
  public void resume() {
    replayController.resume();
  }

  /** Pauses screen recording entirely, but does not stop the current replay. */
  public void pause() {
    replayController.pause();
  }

  /** Returns whether the replay is currently running */
  public boolean isRecording() {
    return replayController.isRecording();
  }

  /** The id of the currently running replay or {@link SentryId#EMPTY_ID} if no replay is running */
  @NotNull
  public SentryId getReplayId() {
    return replayController.getReplayId();
  }
}
