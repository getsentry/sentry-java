package io.sentry;

import io.sentry.protocol.SentryId;
import javax.swing.text.View;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public class ReplayApi {

  private final @NotNull ReplayController replayController;

  public ReplayApi(final @NotNull ReplayController replayController) {
    this.replayController = replayController;
  }

  public void start() {
    replayController.start();
  }

  public void stop() {
    replayController.stop();
  }

  public void pause() {
    replayController.pause();
  }

  public void resume() {
    replayController.resume();
  }

  public boolean isRecording() {
    return replayController.isRecording();
  }

  @NotNull
  public SentryId getReplayId() {
    return replayController.getReplayId();
  }

  public void redactView(final @NotNull View view) {
    replayController.redactView(view);
  }

  public void ignoreView(final @NotNull View view) {
    replayController.ignoreView(view);
  }
}
