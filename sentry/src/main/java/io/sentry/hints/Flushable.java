package io.sentry.hints;

/** marker interface that awaits events to be flushed */
public interface Flushable {

  boolean waitFlush();
}
