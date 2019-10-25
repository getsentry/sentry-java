package io.sentry.core;

import io.sentry.core.protocol.SentryStackFrame;
import io.sentry.core.protocol.SentryStackTrace;
import io.sentry.core.protocol.SentryThread;
import io.sentry.core.util.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class SentryThreadFactory {

  // Assumes its being called from the crashed thread.
  List<SentryThread> getCurrentThreadsForCrash() {
    return getCurrentThreads(Thread.currentThread());
  }

  // Doesn't mark a thread either crashed or not.
  List<SentryThread> getCurrentThreads() {
    return getCurrentThreads(null);
  }

  private List<SentryThread> getCurrentThreads(@Nullable Thread crashedThread) {
    Map<Thread, StackTraceElement[]> threads = Thread.getAllStackTraces();
    List<SentryThread> result = new ArrayList<>();

    for (Map.Entry<Thread, StackTraceElement[]> item : threads.entrySet()) {
      result.add(
          getSentryThread(crashedThread, Thread.currentThread(), item.getValue(), item.getKey()));
    }

    return result;
  }

  private SentryThread getSentryThread(
      @Nullable Thread crashedThread,
      Thread currentThread,
      StackTraceElement[] stackFramesElements,
      Thread thread) {
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

    List<SentryStackFrame> frames = new ArrayList<>();
    for (StackTraceElement element : stackFramesElements) {
      frames.add(getSentryStackFrame(element));
    }

    if (frames.size() > 0) {
      sentryThread.setStacktrace(new SentryStackTrace(frames));
    }

    return sentryThread;
  }

  private SentryStackFrame getSentryStackFrame(StackTraceElement element) {
    SentryStackFrame sentryStackFrame = new SentryStackFrame();
    sentryStackFrame.setModule(element.getClassName());
    sentryStackFrame.setFilename(element.getFileName());
    if (element.getLineNumber() >= 0) {
      sentryStackFrame.setLineno(element.getLineNumber());
    }
    sentryStackFrame.setFunction(element.getMethodName());
    sentryStackFrame.setNative(element.isNativeMethod());
    return sentryStackFrame;
  }
}
