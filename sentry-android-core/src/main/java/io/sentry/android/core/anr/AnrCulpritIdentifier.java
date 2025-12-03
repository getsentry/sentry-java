package io.sentry.android.core.anr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class AnrCulpritIdentifier {

  // common Java and Android packages who are less relevant for being the actual culprit
  private static final List<String> lowQualityPackages = new ArrayList<>(9);

  static {
    lowQualityPackages.add("java.lang");
    lowQualityPackages.add("java.util");
    lowQualityPackages.add("android.app");
    lowQualityPackages.add("android.os.Handler");
    lowQualityPackages.add("android.os.Looper");
    lowQualityPackages.add("android.view");
    lowQualityPackages.add("android.widget");
    lowQualityPackages.add("com.android.internal");
    lowQualityPackages.add("com.google.android");
  }

  /**
   * @param stacks the captured stacktraces
   * @return the most common occurring stacktrace identified as the culprit
   */
  @Nullable
  public static AggregatedStackTrace identify(final @NotNull List<AnrStackTrace> stacks) {
    if (stacks.isEmpty()) {
      return null;
    }

    // fold all stacktraces and count their occurrences
    final @NotNull Map<Integer, AggregatedStackTrace> stackTraceMap = new HashMap<>();
    for (final AnrStackTrace stackTrace : stacks) {

      if (stackTrace.stack.length < 2) {
        continue;
      }

      // entry 0 is the most detailed element in the stacktrace
      // so create sub-stacks (1..n, 2..n, ...) to capture the most common root cause of an ANR
      for (int i = 0; i < stackTrace.stack.length - 1; i++) {
        // TODO using hashcode is actually a bad key
        final int key = subArrayHashCode(stackTrace.stack, i, stackTrace.stack.length - 1);
        int quality = 10;
        final String clazz = stackTrace.stack[i].getClassName();
        for (String ignoredPackage : lowQualityPackages) {
          if (clazz.startsWith(ignoredPackage)) {
            quality = 1;
            break;
          }
        }

        @Nullable AggregatedStackTrace aggregatedStackTrace = stackTraceMap.get(key);
        if (aggregatedStackTrace == null) {
          aggregatedStackTrace =
              new AggregatedStackTrace(
                  stackTrace.stack,
                  i,
                  stackTrace.stack.length - 1,
                  stackTrace.timestampMs,
                  quality);
          stackTraceMap.put(key, aggregatedStackTrace);
        } else {
          aggregatedStackTrace.add(stackTrace.timestampMs);
        }
      }
    }

    if (stackTraceMap.isEmpty()) {
      return null;
    }

    // the deepest stacktrace with most count wins
    return Collections.max(
        stackTraceMap.values(),
        (c1, c2) -> {
          final int countComparison = Integer.compare(c1.count * c1.quality, c2.count * c2.quality);
          if (countComparison == 0) {
            return Integer.compare(c1.depth, c2.depth);
          }
          return countComparison;
        });
  }

  private static int subArrayHashCode(
      final @NotNull Object[] arr, final int stackStartIdx, final int stackEndIdx) {
    int result = 1;
    for (int i = stackStartIdx; i <= stackEndIdx; i++) {
      final Object item = arr[i];
      result = 31 * result + item.hashCode();
    }
    return result;
  }
}
