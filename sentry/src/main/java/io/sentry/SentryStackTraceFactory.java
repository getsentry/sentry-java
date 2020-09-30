package io.sentry;

import io.sentry.protocol.SentryStackFrame;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** class responsible for converting Java StackTraceElements to SentryStackFrames */
final class SentryStackTraceFactory {

  /** list of inApp excludes */
  private final @Nullable List<String> inAppExcludes;

  /** list of inApp includes */
  private final @Nullable List<String> inAppIncludes;

  public SentryStackTraceFactory(
      @Nullable final List<String> inAppExcludes, @Nullable List<String> inAppIncludes) {
    this.inAppExcludes = inAppExcludes;
    this.inAppIncludes = inAppIncludes;
  }

  /**
   * convert an Array of Java StackTraceElements to a list of SentryStackFrames
   *
   * @param elements Array of Java StackTraceElements
   * @return list of SentryStackFrames or null if none
   */
  @Nullable
  List<SentryStackFrame> getStackFrames(@Nullable final StackTraceElement[] elements) {
    List<SentryStackFrame> sentryStackFrames = null;

    if (elements != null && elements.length > 0) {
      sentryStackFrames = new ArrayList<>();
      for (StackTraceElement item : elements) {
        if (item != null) {

          // we don't want to add our own frames
          final String className = item.getClassName();
          if (className.startsWith("io.sentry.")) {
            continue;
          }

          final SentryStackFrame sentryStackFrame = new SentryStackFrame();
          // https://docs.sentry.io/development/sdk-dev/features/#in-app-frames
          sentryStackFrame.setInApp(isInApp(className));
          sentryStackFrame.setModule(className);
          sentryStackFrame.setFunction(item.getMethodName());
          sentryStackFrame.setFilename(item.getFileName());
          // Protocol doesn't accept negative line numbers.
          // The runtime seem to use -2 as a way to signal a native method
          if (item.getLineNumber() >= 0) {
            sentryStackFrame.setLineno(item.getLineNumber());
          }
          sentryStackFrame.setNative(item.isNativeMethod());
          sentryStackFrames.add(sentryStackFrame);
        }
      }
      Collections.reverse(sentryStackFrames);
    }

    return sentryStackFrames;
  }

  /**
   * Returns if the className is InApp or not.
   *
   * @param className the className
   * @return true if it is or false otherwise
   */
  @TestOnly
  boolean isInApp(final @Nullable String className) {
    if (className == null || className.isEmpty()) {
      return true;
    }

    if (inAppIncludes != null) {
      for (String include : inAppIncludes) {
        if (className.startsWith(include)) {
          return true;
        }
      }
    }
    if (inAppExcludes != null) {
      for (String exclude : inAppExcludes) {
        if (className.startsWith(exclude)) {
          return false;
        }
      }
    }
    return false;
  }
}
