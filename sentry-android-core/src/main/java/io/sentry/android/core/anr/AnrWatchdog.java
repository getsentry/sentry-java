package io.sentry.android.core.anr;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import io.sentry.Attachment;
import io.sentry.Hint;
import io.sentry.Sentry;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AnrWatchdog {

  private static final String TAG = "AnrWatchdog";

  private static final long POLLING_INTERVAL_MS = 100;
  private static final long THRESHOLD_SUSPICION_MS = 1000;
  private static final long THRESHOLD_ANR_MS = 4000;

  private final AtomicBoolean enabled = new AtomicBoolean(true);
  private final LinkedList<StackTrace> stacks = new LinkedList<>();
  private volatile long lastMainThreadExecutionTime = System.currentTimeMillis();
  private final Runnable updater = () -> lastMainThreadExecutionTime = System.currentTimeMillis();
  private volatile MainThreadState mainThreadState = MainThreadState.IDLE;

  public void start() {
    // get main thread Handler so we can post messages
    final Thread mainThread = Thread.currentThread();
    final Looper mainLooper = Looper.getMainLooper();
    final Handler mainHandler = new Handler(mainLooper);

    new Thread(
            () -> {
              try {
                while (enabled.get()) {
                  try {
                    final long now = System.currentTimeMillis();
                    final long diff = now - lastMainThreadExecutionTime;

                    if (diff < 1000) {
                      stacks.clear();
                      mainThreadState = MainThreadState.IDLE;
                    }

                    if (mainThreadState == MainThreadState.IDLE && diff > THRESHOLD_SUSPICION_MS) {
                      Log.w(TAG, "ANR: main thread is suspicious");
                      stacks.clear();
                      mainThreadState = MainThreadState.SUSPICIOUS;
                    }

                    // if we are suspicious, we need to collect stack traces
                    if (mainThreadState == MainThreadState.SUSPICIOUS) {
                      if (stacks.size() >= THRESHOLD_ANR_MS / POLLING_INTERVAL_MS) {
                        stacks.removeFirst();
                      }
                      stacks.add(
                          new StackTrace(System.currentTimeMillis(), mainThread.getStackTrace()));
                    }

                    // if we are suspicious and the main thread is blocked for more than 4 seconds,
                    // we have an ANR
                    if (mainThreadState == MainThreadState.SUSPICIOUS && diff > THRESHOLD_ANR_MS) {
                      Log.w(TAG, "ANR: main thread ANR threshold reached, reporting!");

                      mainThreadState = MainThreadState.ANR_DETECTED;

                      final @Nullable AggregatedStackTrace culprit = analyzeStackTraces(stacks);
                      if (culprit != null) {
                        // send to sentry
                        final Exception e = new AnrException("Watchdog ANR");
                        e.setStackTrace(culprit.getStack());

                        final String stackTracesAsString = serializeStacks();

                        final Hint hint = new Hint();
                        hint.addAttachment(
                            new Attachment(
                                stackTracesAsString.getBytes(StandardCharsets.UTF_8),
                                "stacktraces.txt",
                                "text/plain"));
                        Sentry.captureException(e, hint);
                      }
                    }

                    mainHandler.removeCallbacks(updater);
                    mainHandler.post(updater);

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

  private @NonNull String serializeStacks() {
    final StringBuilder builder = new StringBuilder();
    for (StackTrace stack : stacks) {
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

  public void stop() {
    enabled.set(false);
  }

  @Nullable
  private AggregatedStackTrace analyzeStackTraces(final @NotNull List<StackTrace> dumps) {
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

        @Nullable AggregatedStackTrace aggregatedStackTrace = stackTraceMap.get(key);
        if (aggregatedStackTrace == null) {
          aggregatedStackTrace =
              new AggregatedStackTrace(dump.stack, i, dump.stack.length - 1, dump.timestampMs);
          stackTraceMap.put(key, aggregatedStackTrace);
        } else {
          aggregatedStackTrace.add(dump.timestampMs);
        }
      }
    }

    // the deepest stacktrace with most count wins
    return Collections.max(
        stackTraceMap.values(),
        (c1, c2) -> {
          final int countComparison = Integer.compare(c1.count, c2.count);
          if (countComparison == 0) {
            return Integer.compare(c1.quality, c2.quality);
          }
          return countComparison;
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
}
