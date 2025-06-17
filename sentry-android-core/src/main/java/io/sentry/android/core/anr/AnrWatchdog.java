package io.sentry.android.core.anr;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import io.sentry.Attachment;
import io.sentry.Hint;
import io.sentry.ILogger;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A watchdog that monitors the main thread for ANR (Application Not Responding) conditions. Unlike
 * classic watchdogs, it captures multiple stack traces of the main thread, folds them together and
 * reports the most common stack trace as the ANR "culprit" exception.
 *
 * <p>The implementation tries to keep the footprint on the main thread low by using a separate
 * thread to monitor the main thread, and analyze the stacktraces.
 *
 * <p>On top of that all collected stack traces are attached to the event as a text file.
 *
 * <p>Inspired by <a
 * href="https://github.com/brendangregg/FlameGraph">github.com/brendangregg/FlameGraph</a>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 *   final AtomicReference<AnrWatchdog> watchdogRef = new AtomicReference<>();
 *   ProcessLifecycleOwner.get().getLifecycle().addObserver((LifecycleEventObserver) (lifecycleOwner, event) -> {
 *       @Nullable AnrWatchdog watchdog = watchdogRef.get();
 *       if (event == Lifecycle.Event.ON_START) {
 *           watchdog = new AnrWatchdog();
 *           watchdogRef.set(watchdog);
 *       } else if (event == Lifecycle.Event.ON_STOP) {
 *           if (watchdog != null) {
 *               watchdog.stop();
 *               watchdogRef.set(null);
 *           }
 *       }
 *   });
 * }
 * *
 * </pre>
 */
public class AnrWatchdog {

  private static final long POLLING_INTERVAL_MS = 99;
  private static final long THRESHOLD_SUSPICION_MS = 1000;
  private static final long THRESHOLD_ANR_MS = 4000;

  // the maximum number of stack traces to keep in memory
  private static final int MAX_STACK_SIZE = (int) (THRESHOLD_ANR_MS / POLLING_INTERVAL_MS);

  private final AtomicBoolean enabled = new AtomicBoolean(true);
  private final LinkedList<StackTrace> stacks = new LinkedList<>();
  private final Runnable updater = () -> lastMainThreadExecutionTime = SystemClock.uptimeMillis();
  private volatile long lastMainThreadExecutionTime = SystemClock.uptimeMillis();
  private volatile MainThreadState mainThreadState = MainThreadState.IDLE;

  public AnrWatchdog(final @NotNull ILogger logger) {
    final @NotNull Looper mainLooper = Looper.getMainLooper();
    final @NotNull Thread mainThread = mainLooper.getThread();
    final @NotNull Handler mainThreadHandler = new Handler(mainLooper);

    new Thread(
            () -> {
              try {
                final List<String> ignoredPackages = getSystemPackages();
                while (enabled.get()) {
                  try {
                    final long nowMs = SystemClock.uptimeMillis();
                    final long diffMs = nowMs - lastMainThreadExecutionTime;

                    if (diffMs < 1000) {
                      stacks.clear();
                      mainThreadState = MainThreadState.IDLE;
                    }

                    if (mainThreadState == MainThreadState.IDLE
                        && diffMs > THRESHOLD_SUSPICION_MS) {
                      logger.log(SentryLevel.INFO, "ANR Watchdog: main thread is suspicious");
                      stacks.clear();
                      mainThreadState = MainThreadState.SUSPICIOUS;
                    }

                    // once we're in suspicious state, we need to collect stack traces
                    if (mainThreadState == MainThreadState.SUSPICIOUS) {
                      if (stacks.size() >= MAX_STACK_SIZE) {
                        stacks.removeFirst();
                      }
                      final long start = SystemClock.uptimeMillis();
                      stacks.add(
                          new StackTrace(System.currentTimeMillis(), mainThread.getStackTrace()));
                      if (logger.isEnabled(SentryLevel.DEBUG)) {
                        final long duration = SystemClock.uptimeMillis() - start;
                        logger.log(
                            SentryLevel.DEBUG,
                            "ANR Watchdog: capturing main thread stacktrace in " + duration + "ms");
                      }
                    }

                    // if we are suspicious and the main thread is blocked for more than 4 seconds,
                    // we have an ANR
                    if (mainThreadState == MainThreadState.SUSPICIOUS
                        && diffMs > THRESHOLD_ANR_MS) {
                      logger.log(
                          SentryLevel.INFO, "ANR Watchdog: main thread ANR threshold reached");
                      mainThreadState = MainThreadState.ANR_DETECTED;

                      final @Nullable AggregatedStackTrace culprit =
                          determineCulprit(stacks, ignoredPackages);
                      if (culprit != null) {
                        final Exception e = culprit.toException();
                        final Hint hint = new Hint();
                        hint.addAttachment(
                            new Attachment(
                                serializeStacks(stacks).getBytes(StandardCharsets.UTF_8),
                                "stacktraces.txt",
                                "text/plain"));
                        Sentry.captureException(e, hint);
                      } else {
                        logger.log(
                            SentryLevel.INFO, "ANR Watchdog: no culprit found in stack traces");
                      }
                    }

                    mainThreadHandler.removeCallbacks(updater);
                    mainThreadHandler.post(updater);

                    // noinspection BusyWait
                    Thread.sleep(POLLING_INTERVAL_MS);
                  } catch (InterruptedException e) {
                    // ignored
                  }
                }
              } catch (Throwable t) {
                Sentry.captureException(t);
              }
            })
        .start();
  }

  @NonNull
  private static List<String> getSystemPackages() {
    return Arrays.asList(
        "java.lang",
        "java.util",
        "android.app",
        "android.os.Handler",
        "android.os.Looper",
        "android.view",
        "android.widget",
        "com.android.internal",
        "com.google.android");
  }

  public void stop() {
    enabled.set(false);
  }

  private static @NotNull String serializeStacks(final List<StackTrace> stackTraces) {
    final StringBuilder builder = new StringBuilder();
    for (final StackTrace stack : stackTraces) {
      builder.append("Timestamp: ").append(stack.timestampMs).append("\n");
      for (final StackTraceElement frame : stack.stack) {
        builder
            .append(frame.getClassName())
            .append(".")
            .append(frame.getMethodName())
            .append("(")
            .append(frame.getFileName())
            .append(":")
            .append(frame.getLineNumber())
            .append(")")
            .append("\n");
      }
      builder.append("\n");
    }
    return builder.toString();
  }

  @Nullable
  private AggregatedStackTrace determineCulprit(
      final @NotNull List<StackTrace> dumps, final @NotNull List<String> ignoredPackages) {
    if (dumps.isEmpty()) {
      return null;
    }

    // fold all stacktraces and count their occurrences
    final Map<Integer, AggregatedStackTrace> stackTraceMap = new HashMap<>();
    for (final StackTrace dump : dumps) {

      // entry 0 is the most detailed element in the stacktrace
      // so create sub-stacks (1..n, 2..n, ...) to capture the most common root cause of an ANR
      for (int i = 0; i < dump.stack.length - 1; i++) {

        final int key = subArrayHashCode(dump.stack, i, dump.stack.length - 1);

        int quality = 10;
        final String clazz = dump.stack[i].getClassName();
        for (String ignoredPackage : ignoredPackages) {
          if (clazz.startsWith(ignoredPackage)) {
            quality = 1;
            break;
          }
        }

        @Nullable AggregatedStackTrace aggregatedStackTrace = stackTraceMap.get(key);
        if (aggregatedStackTrace == null) {
          aggregatedStackTrace =
              new AggregatedStackTrace(
                  dump.stack, i, dump.stack.length - 1, dump.timestampMs, quality);
          stackTraceMap.put(key, aggregatedStackTrace);
        } else {
          aggregatedStackTrace.add(dump.timestampMs);
        }
      }
    }

    return Collections.max(
        stackTraceMap.values(),
        (c1, c2) -> {
          final int scoreComparison = Integer.compare(c1.count * c1.quality, c2.count * c2.quality);
          if (scoreComparison == 0) {
            // if the scores are equal, a more detailed stacktrace is preferred
            return Integer.compare(c1.depth, c2.depth);
          }
          return scoreComparison;
        });
  }

  private int subArrayHashCode(
      final @NotNull Object[] arr, final int stackStartIdx, final int stackEndIdx) {
    int result = 1;
    for (int i = stackStartIdx; i <= stackEndIdx; i++) {
      final Object item = arr[i];
      result = 31 * result + item.hashCode();
    }
    return result;
  }

  enum MainThreadState {
    IDLE,
    SUSPICIOUS,
    ANR_DETECTED,
  }
}
