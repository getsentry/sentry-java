package io.sentry.core;

import io.sentry.core.protocol.SentryStackFrame;
import io.sentry.core.protocol.SentryStackTrace;
import io.sentry.core.protocol.SentryThread;
import io.sentry.core.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** class responsible for converting Java Threads to SentryThreads */
final class SentryThreadFactory {

  /** the SentryStackTraceFactory */
  private final @NotNull SentryStackTraceFactory sentryStackTraceFactory;

  /**
   * ctor SentryThreadFactory that takes a SentryStackTraceFactory
   *
   * @param sentryStackTraceFactory the SentryStackTraceFactory
   */
  public SentryThreadFactory(final @NotNull SentryStackTraceFactory sentryStackTraceFactory) {
    this.sentryStackTraceFactory =
        Objects.requireNonNull(sentryStackTraceFactory, "The SentryStackTraceFactory is required.");
  }

  /**
   * Converts a list of all current threads to a list of SentryThread Assumes its being called from
   * the crashed thread.
   *
   * @return a list of SentryThread
   */
  @Nullable
  List<SentryThread> getCurrentThreads() {
    return getCurrentThreads(Thread.getAllStackTraces());
  }

  /**
   * Converts a list of all current threads to a list of SentryThread Assumes its being called from
   * the crashed thread.
   *
   * @param threads a map with all the current threads and stacktraces
   * @return a list of SentryThread or null if none
   */
  @TestOnly
  @Nullable
  List<SentryThread> getCurrentThreads(final @NotNull Map<Thread, StackTraceElement[]> threads) {
    List<SentryThread> result = null;

    final Thread currentThread = Thread.currentThread();

    if (threads.size() > 0) {
      result = new ArrayList<>();

      // https://issuetracker.google.com/issues/64122757
      if (!threads.containsKey(currentThread)) {
        threads.put(currentThread, currentThread.getStackTrace());
      }

      for (Map.Entry<Thread, StackTraceElement[]> item : threads.entrySet()) {
        result.add(getSentryThread(currentThread, item.getValue(), item.getKey()));
      }
    }

    return result;
  }

  /**
   * Converts a current thread to a SentryThread
   *
   * @param currentThread the currentThread
   * @param stackFramesElements the stack traces of the current thread
   * @param thread the thread to be converted
   * @return a SentryThread
   */
  private @NotNull SentryThread getSentryThread(
      final @NotNull Thread currentThread,
      final @NotNull StackTraceElement[] stackFramesElements,
      final @NotNull Thread thread) {
    final SentryThread sentryThread = new SentryThread();

    sentryThread.setName(thread.getName());
    sentryThread.setPriority(thread.getPriority());
    sentryThread.setId(thread.getId());
    sentryThread.setDaemon(thread.isDaemon());
    sentryThread.setState(thread.getState().name());
    sentryThread.setCrashed(thread == currentThread);

    final List<SentryStackFrame> frames =
        sentryStackTraceFactory.getStackFrames(stackFramesElements);

    if (frames != null && frames.size() > 0) {
      sentryThread.setStacktrace(new SentryStackTrace(frames));
    }

    return sentryThread;
  }
}
