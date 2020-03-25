package io.sentry.core.hints;

/** marker interface that awaits events to be flushed */
public interface Flushable {

  boolean waitFlush();
}
