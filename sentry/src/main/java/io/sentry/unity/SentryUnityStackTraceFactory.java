package io.sentry.unity;

import io.sentry.SentryOptions;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.util.CollectionUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** class responsible for converting Java StackTraceElements to SentryStackFrames */
@ApiStatus.Internal
public final class SentryUnityStackTraceFactory {

  private static final int STACKTRACE_FRAME_LIMIT = 100;
  private final @NotNull SentryOptions options;

  public SentryUnityStackTraceFactory(final @NotNull SentryOptions options) {
    this.options = options;
  }

  /**
   * convert an Array of Java StackTraceElements to a list of SentryStackFrames
   *
   * @param elements Array of Java StackTraceElements
   * @return list of SentryStackFrames or null if none
   */
  @Nullable
  public List<SentryStackFrame> getStackFrames(@Nullable final StackTraceElement[] elements) {
    List<SentryStackFrame> sentryStackFrames = null;

    if (elements != null && elements.length > 0) {
      sentryStackFrames = new ArrayList<>();
      for (StackTraceElement item : elements) {
        if (item != null) {

          final String className = item.getClassName();
          final SentryStackFrame sentryStackFrame = new SentryStackFrame();
          // https://docs.sentry.io/development/sdk-dev/features/#in-app-frames
          //sentryStackFrame.setInApp(isInApp(className));
          sentryStackFrame.setPackage(className);
          sentryStackFrame.setImageAddr(item.getMethodName());
          //sentryStackFrame.setFilename(item.getFileName());
          // Protocol doesn't accept negative line numbers.
          // The runtime seem to use -2 as a way to signal a native method
          if (item.getLineNumber() >= 0) {
            sentryStackFrame.setLineno(item.getLineNumber());
          }
          sentryStackFrame.setNative(true);
          sentryStackFrames.add(sentryStackFrame);

          // hard cap to not exceed payload size limit
          if (sentryStackFrames.size() >= STACKTRACE_FRAME_LIMIT) {
            break;
          }
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
  @Nullable
  public Boolean isInApp(final @Nullable String className) {
    if (className == null || className.isEmpty()) {
      return true;
    }

    final List<String> inAppIncludes = options.getInAppIncludes();
    for (String include : inAppIncludes) {
      if (className.startsWith(include)) {
        return true;
      }
    }

    final List<String> inAppExcludes = options.getInAppExcludes();
    for (String exclude : inAppExcludes) {
      if (className.startsWith(exclude)) {
        return false;
      }
    }

    return null;
  }
}
