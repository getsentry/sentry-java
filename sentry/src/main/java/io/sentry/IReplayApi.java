package io.sentry;

/**
 * Controls Session Replay. Methods may be called from any thread and return before the requested
 * operation completes.
 */
public interface IReplayApi {

  /** Starts a new replay session. Does nothing if a replay is already being recorded. */
  void start();

  /**
   * Starts replay buffering. The rolling buffer is sent when {@link #flush()} is called or an error
   * is captured and selected by {@link SentryReplayOptions#getOnErrorSampleRate()}. After the
   * buffer is sent, recording continues in session mode unless the process is terminating.
   */
  void startBuffering();

  /**
   * Stops the current replay in either session or buffer mode. A subsequent {@link #start()} begins
   * a new replay session.
   */
  void stop();

  /**
   * Pauses replay recording in either session or buffer mode until {@link #resume()} is called. If
   * the SDK automatically starts a new replay session in the same process, the new replay remains
   * paused. This can be used to avoid recording sensitive screens, such as PIN entry.
   */
  void pause();

  /** Resumes a replay paused with {@link #pause()}. */
  void resume();

  /**
   * Stops recording touch events until {@link #resumeTouchTracking()} is called. Screenshots are
   * still recorded. This can be used on sensitive screens, such as a custom PIN pad, where the
   * screen is masked but the touch positions could reveal the input.
   *
   * <p>Touch tracking stays paused for all replays in the same process, also if the replay is
   * stopped and started again. {@link #resume()} does not resume touch tracking. Has effect
   * immediately, also before a replay is started.
   */
  void pauseTouchTracking();

  /**
   * Resumes touch tracking paused with {@link #pauseTouchTracking()}. Touch events are recorded
   * again only while the replay is not paused with {@link #pause()}.
   */
  void resumeTouchTracking();

  /**
   * Immediately sends the current replay data to Sentry in either session or buffer mode. A
   * buffering replay continues in session mode after the buffer is sent. If replay is not
   * recording, starts a new replay session.
   */
  void flush();

  /**
   * Draws a masking overlay on top of the screen to help visualize which parts of the screen are
   * masked by Session Replay. This is only useful for debugging purposes and should not be used in
   * production environments.
   *
   * <p>Expect the top level view to be invalidated more often than usual, as the overlay is drawn
   * on top of it.
   */
  void enableDebugMaskingOverlay();

  void disableDebugMaskingOverlay();
}
