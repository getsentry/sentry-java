package io.sentry.core;

import io.sentry.core.protocol.SentryStackFrame;
import io.sentry.core.util.Nullable;
import java.util.ArrayList;
import java.util.List;

/** class responsible for converting Java StackTraceElements to SentryStackFrames */
class SentryStackTraceFactory {

  /**
   * convert an Array of Java StackTraceElements to a list of SentryStackFrames
   *
   * @param elements Array of Java StackTraceElements
   * @return list of SentryStackFrames
   */
  List<SentryStackFrame> getStackFrames(@Nullable final StackTraceElement[] elements) {
    List<SentryStackFrame> sentryStackFrames = new ArrayList<>();

    if (elements != null) {
      for (StackTraceElement item : elements) {
        SentryStackFrame sentryStackFrame = new SentryStackFrame();
        sentryStackFrame.setModule(item.getClassName());
        sentryStackFrame.setFunction(item.getMethodName());
        sentryStackFrame.setFilename(item.getFileName());
        sentryStackFrame.setLineno(item.getLineNumber());
        sentryStackFrame.setNative(item.isNativeMethod());

        sentryStackFrames.add(sentryStackFrame);
      }
    }

    return sentryStackFrames;
  }
}
