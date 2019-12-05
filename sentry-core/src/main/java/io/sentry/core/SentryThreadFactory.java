package io.sentry.core;

import io.sentry.core.protocol.SentryStackFrame;
import io.sentry.core.protocol.SentryStackTrace;
import io.sentry.core.protocol.SentryThread;
import io.sentry.core.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class SentryThreadFactory {

  private final SentryStackTraceFactory sentryStackTraceFactory;

  public SentryThreadFactory(SentryStackTraceFactory sentryStackTraceFactory) {
    this.sentryStackTraceFactory =
        Objects.requireNonNull(sentryStackTraceFactory, "The SentryStackTraceFactory is required.");
  }

  // Assumes its being called from the crashed (errored) thread.
  List<SentryThread> getCurrentThreads() {
    Map<Thread, StackTraceElement[]> threads = Thread.getAllStackTraces();
    List<SentryThread> result = new ArrayList<>();

    Thread currentThread = Thread.currentThread();
    for (Map.Entry<Thread, StackTraceElement[]> item : threads.entrySet()) {
      result.add(getSentryThread(currentThread, item.getValue(), item.getKey()));
    }

    return result;
  }

  private SentryThread getSentryThread(
      final Thread currentThread,
      final StackTraceElement[] stackFramesElements,
      final Thread thread) {
    SentryThread sentryThread = new SentryThread();

    sentryThread.setName(thread.getName());
    sentryThread.setPriority(thread.getPriority());
    sentryThread.setId(thread.getId());
    sentryThread.setDaemon(thread.isDaemon());
    sentryThread.setState(thread.getState().name());
    sentryThread.setCrashed(thread == currentThread);

    List<SentryStackFrame> frames = sentryStackTraceFactory.getStackFrames(stackFramesElements);

    if (frames.size() > 0) {
      sentryThread.setStacktrace(new SentryStackTrace(frames));
    }

    return sentryThread;
  }
}
