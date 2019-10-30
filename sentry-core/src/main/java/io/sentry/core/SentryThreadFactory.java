package io.sentry.core;

import io.sentry.core.protocol.SentryStackFrame;
import io.sentry.core.protocol.SentryStackTrace;
import io.sentry.core.protocol.SentryThread;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

final class SentryThreadFactory {

  // Assumes its being called from the crashed thread.
  List<SentryThread> getCurrentThreadsForCrash() {
    return getCurrentThreads(Thread.currentThread());
  }

  // Doesn't mark a thread either crashed or not.
  List<SentryThread> getCurrentThreads() {
    return getCurrentThreads(null);
  }

  private List<SentryThread> getCurrentThreads(@Nullable final Thread crashedThread) {
    Map<Thread, StackTraceElement[]> threads = Thread.getAllStackTraces();
    List<SentryThread> result = new ArrayList<>();

    for (Map.Entry<Thread, StackTraceElement[]> item : threads.entrySet()) {
      result.add(
          getSentryThread(crashedThread, Thread.currentThread(), item.getValue(), item.getKey()));
    }

    return result;
  }

  private SentryThread getSentryThread(
      @Nullable final Thread crashedThread,
      final Thread currentThread,
      final StackTraceElement[] stackFramesElements,
      final Thread thread) {
    SentryThread sentryThread = new SentryThread();

    sentryThread.setName(thread.getName());
    sentryThread.setPriority(thread.getPriority());
    sentryThread.setId(thread.getId());
    sentryThread.setDaemon(thread.isDaemon());
    sentryThread.setState(thread.getState().name());
    if (crashedThread != null) {
      sentryThread.setCrashed(crashedThread == thread);
    }
    sentryThread.setCurrent(thread == currentThread);

    SentryStackTraceFactory sentryStackTraceFactory = new SentryStackTraceFactory();
    List<SentryStackFrame> frames = sentryStackTraceFactory.getStackFrames(stackFramesElements);

    if (frames.size() > 0) {
      sentryThread.setStacktrace(new SentryStackTrace(frames));
    }

    return sentryThread;
  }
}
