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
  private static final List<String> systemAndFrameWorkPackages = new ArrayList<>(9);

  static {
    systemAndFrameWorkPackages.add("java.lang");
    systemAndFrameWorkPackages.add("java.util");
    systemAndFrameWorkPackages.add("android.app");
    systemAndFrameWorkPackages.add("android.os.Handler");
    systemAndFrameWorkPackages.add("android.os.Looper");
    systemAndFrameWorkPackages.add("android.view");
    systemAndFrameWorkPackages.add("android.widget");
    systemAndFrameWorkPackages.add("com.android.internal");
    systemAndFrameWorkPackages.add("com.google.android");
  }

  private static final class StackTraceKey {
    private final @NotNull StackTraceElement[] stack;
    private final int startIdx;
    private final int endIdx;
    private final int hashCode;

    StackTraceKey(final @NotNull StackTraceElement[] stack, final int startIdx, final int endIdx) {
      this.stack = stack;
      this.startIdx = startIdx;
      this.endIdx = endIdx;
      this.hashCode = computeHashCode();
    }

    private int computeHashCode() {
      int result = 1;
      for (int i = startIdx; i <= endIdx; i++) {
        result = 31 * result + stack[i].hashCode();
      }
      return result;
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    @Override
    public boolean equals(final Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof StackTraceKey)) {
        return false;
      }

      final @NotNull StackTraceKey other = (StackTraceKey) obj;

      if (hashCode != other.hashCode) {
        return false;
      }

      final int length = endIdx - startIdx + 1;
      final int otherLength = other.endIdx - other.startIdx + 1;
      if (length != otherLength) {
        return false;
      }

      for (int i = 0; i < length; i++) {
        if (!stack[startIdx + i].equals(other.stack[other.startIdx + i])) {
          return false;
        }
      }

      return true;
    }
  }

  /**
   * @param stacks a list of stack traces to analyze
   * @return the most common occurring stacktrace identified as the culprit
   */
  @Nullable
  public static AggregatedStackTrace identify(final @NotNull List<AnrStackTrace> stacks) {
    if (stacks.isEmpty()) {
      return null;
    }

    // fold all stacktraces and count their occurrences
    final @NotNull Map<StackTraceKey, AggregatedStackTrace> stackTraceMap = new HashMap<>();
    for (final @NotNull AnrStackTrace stackTrace : stacks) {
      if (stackTrace.stack.length < 2) {
        continue;
      }

      // stack[0] is the most detailed element in the stacktrace
      // iterate from end to start (length-1 → 0) creating sub-stacks (i..n-1) to find the most
      // common root cause
      // count app frames from the end to compute quality scores
      int appFramesCount = 0;

      for (int i = stackTrace.stack.length - 1; i >= 0; i--) {

        final @NotNull String topMostClassName = stackTrace.stack[i].getClassName();
        final boolean isSystemFrame = isSystemFrame(topMostClassName);
        if (!isSystemFrame) {
          appFramesCount++;
        }

        final int totalFrames = stackTrace.stack.length - i;
        final float quality = (float) appFramesCount / totalFrames;

        final @NotNull StackTraceKey key =
            new StackTraceKey(stackTrace.stack, i, stackTrace.stack.length - 1);

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
          aggregatedStackTrace.addOccurrence(stackTrace.timestampMs);
        }
      }
    }

    if (stackTraceMap.isEmpty()) {
      return null;
    }

    // the deepest stacktrace with most count wins
    return Collections.max(
        stackTraceMap.values(),
        (c1, c2) ->
            Float.compare(c1.count * c1.quality * c1.depth, c2.count * c2.quality * c2.depth));
  }

  private static boolean isSystemFrame(final @NotNull String clazz) {
    for (final String systemPackage : systemAndFrameWorkPackages) {
      if (clazz.startsWith(systemPackage)) {
        return true;
      }
    }
    return false;
  }
}
