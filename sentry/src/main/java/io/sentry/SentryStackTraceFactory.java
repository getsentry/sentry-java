package io.sentry;

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
public final class SentryStackTraceFactory {

  private static final int STACKTRACE_FRAME_LIMIT = 100;
  private final @NotNull SentryOptions options;

  public SentryStackTraceFactory(final @NotNull SentryOptions options) {
    this.options = options;
  }

  /**
   * convert an Array of Java StackTraceElements to a list of SentryStackFrames
   *
   * @param elements Array of Java StackTraceElements
   * @return list of SentryStackFrames or null if none
   */
  @Nullable
  public List<SentryStackFrame> getStackFrames(
      @Nullable final StackTraceElement[] elements, final boolean includeSentryFrames) {
    List<SentryStackFrame> sentryStackFrames = null;

    if (elements != null && elements.length > 0) {
      sentryStackFrames = new ArrayList<>();
      for (StackTraceElement item : elements) {
        if (item != null) {

          // we don't want to add our own frames
          final String className = item.getClassName();
          if (!includeSentryFrames
              && (className.startsWith("io.sentry.")
                  && !className.startsWith("io.sentry.samples.")
                  && !className.startsWith("io.sentry.mobile."))) {
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
   * Provides the logic to decide whether a className is part of the includes or excludes list of
   * strings. The bias is towards includes, meaning once a className starts with a prefix in the
   * includes list, it immediately returns, ignoring any counter entry in excludes.
   *
   * @param className the className
   * @return true if it is or false otherwise
   */
  @Nullable
  public static Boolean isInApp(
      final @Nullable String className,
      final @NotNull List<String> includes,
      final @NotNull List<String> excludes) {
    if (className == null || className.isEmpty()) {
      return true;
    }

    for (String include : includes) {
      if (className.startsWith(include)) {
        return true;
      }
    }

    for (String exclude : excludes) {
      if (className.startsWith(exclude)) {
        return false;
      }
    }

    return null;
  }

  /**
   * Returns if the className is InApp or not.
   *
   * @param className the className
   * @return true if it is or false otherwise
   */
  @Nullable
  public Boolean isInApp(final @Nullable String className) {
    return isInApp(className, options.getInAppIncludes(), options.getInAppExcludes());
  }

  /**
   * Returns the call stack leading to the exception, including in-app frames and excluding sentry
   * and system frames.
   *
   * @param exception an exception to get the call stack to
   * @return a list of sentry stack frames leading to the exception
   */
  @NotNull
  List<SentryStackFrame> getInAppCallStack(final @NotNull Throwable exception) {
    final StackTraceElement[] stacktrace = exception.getStackTrace();
    final List<SentryStackFrame> frames = getStackFrames(stacktrace, false);
    if (frames == null) {
      return Collections.emptyList();
    }

    final List<SentryStackFrame> inAppFrames =
        CollectionUtils.filterListEntries(frames, (frame) -> Boolean.TRUE.equals(frame.isInApp()));

    if (!inAppFrames.isEmpty()) {
      return inAppFrames;
    }

    // if inAppFrames is empty, most likely we're operating over an obfuscated app, just trying to
    // fallback to all the frames that are not system frames
    return CollectionUtils.filterListEntries(
        frames,
        (frame) -> {
          final String module = frame.getModule();
          boolean isSystemFrame = false;
          if (module != null) {
            isSystemFrame =
                module.startsWith("sun.")
                    || module.startsWith("java.")
                    || module.startsWith("android.")
                    || module.startsWith("com.android.");
          }
          return !isSystemFrame;
        });
  }

  @ApiStatus.Internal
  @NotNull
  public List<SentryStackFrame> getInAppCallStack() {
    return getInAppCallStack(new Exception());
  }
}
