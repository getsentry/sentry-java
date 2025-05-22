package io.sentry;

public interface IReplayApi {

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
