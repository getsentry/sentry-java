package io.sentry.protocol;

import java.util.List;

/** The Sentry stacktrace. */
public class SentryStackTrace {
  private List<SentryStackFrame> frames;

  /**
   * Gets the frames of this stacktrace.
   *
   * @return the frames.
   */
  public List<SentryStackFrame> getFrames() {
    return frames;
  }

  /**
   * Sets the frames of this stacktrace.
   *
   * @param frames the frames.
   */
  public void setFrames(List<SentryStackFrame> frames) {
    this.frames = frames;
  }
}
