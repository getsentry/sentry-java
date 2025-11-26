package io.sentry;

import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import io.sentry.protocol.SentryThread;
import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** class responsible for converting Java Threads to SentryThreads */
@ApiStatus.Internal
public final class SentryThreadFactory {

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
   * Converts the current thread to a SentryThread, it assumes its being called from the captured
   * thread.
   *
   * @return a list of SentryThread with a single item or null
   */
  @Nullable
  List<SentryThread> getCurrentThread(final boolean attachStackTrace) {
    final Map<Thread, StackTraceElement[]> threads = new HashMap<>();
    final Thread currentThread = Thread.currentThread();
    threads.put(currentThread, currentThread.getStackTrace());

    return getCurrentThreads(threads, null, false, attachStackTrace);
  }

  /**
   * Converts a list of all current threads to a list of SentryThread Assumes its being called from
   * the crashed thread.
   *
   * @param mechanismThreadIds list of threadIds that came from exception mechanism
   * @param ignoreCurrentThread if the current thread should be ignored when marking threads as
   *     crashed. This is the case for e.g. watchdog threads which are not the one erroring.
   * @return a list of SentryThread
   */
  @Nullable
  List<SentryThread> getCurrentThreads(
      final @Nullable List<Long> mechanismThreadIds,
      final boolean ignoreCurrentThread,
      final boolean attachStackTrace) {
    return getCurrentThreads(
        Thread.getAllStackTraces(), mechanismThreadIds, ignoreCurrentThread, attachStackTrace);
  }

  @Nullable
  List<SentryThread> getCurrentThreads(
      final @Nullable List<Long> mechanismThreadIds, final boolean attachStackTrace) {
    return getCurrentThreads(
        Thread.getAllStackTraces(), mechanismThreadIds, false, attachStackTrace);
  }

  /**
   * Converts a list of all current threads to a list of SentryThread Assumes its being called from
   * the crashed thread.
   *
   * @param threads a map with all the current threads and stacktraces
   * @param mechanismThreadIds list of threadIds that came from exception mechanism
   * @param ignoreCurrentThread if the current thread should be ignored when marking threads as
   *     crashed. This is the case for e.g. watchdog threads which are not the one erroring.
   * @return a list of SentryThread or null if none
   */
  @TestOnly
  @Nullable
  List<SentryThread> getCurrentThreads(
      final @NotNull Map<Thread, StackTraceElement[]> threads,
      final @Nullable List<Long> mechanismThreadIds,
      final boolean ignoreCurrentThread,
      final boolean attachStackTrace) {
    List<SentryThread> result = null;

    final Thread currentThread = Thread.currentThread();

    if (!threads.isEmpty()) {
      result = new ArrayList<>();

      // https://issuetracker.google.com/issues/64122757
      if (!threads.containsKey(currentThread)) {
        threads.put(currentThread, currentThread.getStackTrace());
      }

      for (Map.Entry<Thread, StackTraceElement[]> item : threads.entrySet()) {

        final Thread thread = item.getKey();
        final boolean crashed =
            (thread == currentThread && !ignoreCurrentThread)
                || (mechanismThreadIds != null
                    && mechanismThreadIds.contains(thread.getId())
                    && !ignoreCurrentThread);

        result.add(getSentryThread(crashed, item.getValue(), item.getKey(), attachStackTrace));
      }
    }

    return result;
  }

  /**
   * Converts a current thread to a SentryThread
   *
   * @param crashed if its the thread that has crashed or not
   * @param stackFramesElements the stack traces of the current thread
   * @param thread the thread to be converted
   * @return a SentryThread
   */
  private @NotNull SentryThread getSentryThread(
      final boolean crashed,
      final @NotNull StackTraceElement[] stackFramesElements,
      final @NotNull Thread thread,
      final boolean attachStacktrace) {
    final SentryThread sentryThread = new SentryThread();

    sentryThread.setName(thread.getName());
    sentryThread.setPriority(thread.getPriority());
    sentryThread.setId(thread.getId());
    sentryThread.setDaemon(thread.isDaemon());
    sentryThread.setState(thread.getState().name());
    sentryThread.setCrashed(crashed);

    if (attachStacktrace) {
      final List<SentryStackFrame> frames =
          sentryStackTraceFactory.getStackFrames(stackFramesElements, false);

      if (frames != null && !frames.isEmpty()) {
        final SentryStackTrace sentryStackTrace = new SentryStackTrace(frames);
        // threads are always gotten either via Thread.currentThread() or Thread.getAllStackTraces()
        sentryStackTrace.setSnapshot(true);

        sentryThread.setStacktrace(sentryStackTrace);
      }
    }

    return sentryThread;
  }
}
