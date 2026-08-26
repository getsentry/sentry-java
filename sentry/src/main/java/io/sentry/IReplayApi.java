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
   * Pauses the current replay in either session or buffer mode. Recording resumes when {@link
   * #resume()} is called or a new replay session starts. This can be used to avoid recording
   * sensitive screens, such as PIN entry.
   */
  void pause();

  /** Resumes a replay paused with {@link #pause()}. */
  void resume();

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
