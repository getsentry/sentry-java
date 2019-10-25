package io.sentry.core.protocol;

import io.sentry.core.IUnknownPropertiesConsumer;
import java.util.List;
import java.util.Map;

/** The Sentry stacktrace. */
public class SentryStackTrace implements IUnknownPropertiesConsumer {
  private List<SentryStackFrame> frames;
  private Map<String, Object> unknown;

  public SentryStackTrace() {}

  public SentryStackTrace(List<SentryStackFrame> frames) {
    this.frames = frames;
  }
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

  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = unknown;
  }
}
