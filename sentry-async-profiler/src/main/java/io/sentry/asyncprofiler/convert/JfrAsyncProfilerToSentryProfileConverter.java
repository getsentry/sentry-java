package io.sentry.asyncprofiler.convert;

import io.sentry.DateUtils;
import io.sentry.Sentry;
import io.sentry.SentryStackTraceFactory;
import io.sentry.asyncprofiler.vendor.asyncprofiler.convert.Arguments;
import io.sentry.asyncprofiler.vendor.asyncprofiler.convert.JfrConverter;
import io.sentry.asyncprofiler.vendor.asyncprofiler.jfr.JfrReader;
import io.sentry.asyncprofiler.vendor.asyncprofiler.jfr.StackTrace;
import io.sentry.asyncprofiler.vendor.asyncprofiler.jfr.event.Event;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.profiling.SentryProfile;
import io.sentry.protocol.profiling.SentrySample;
import io.sentry.protocol.profiling.SentryThreadMetadata;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JfrAsyncProfilerToSentryProfileConverter extends JfrConverter {
  private static final long NANOS_PER_SECOND = 1_000_000_000L;

  private final @NotNull SentryProfile sentryProfile = new SentryProfile();
  private final @NotNull SentryStackTraceFactory stackTraceFactory;

  public JfrAsyncProfilerToSentryProfileConverter(
      JfrReader jfr, Arguments args, @NotNull SentryStackTraceFactory stackTraceFactory) {
    super(jfr, args);
    this.stackTraceFactory = stackTraceFactory;
  }

  @Override
  protected void convertChunk() {
    collector.forEach(new ProfileEventVisitor(sentryProfile, stackTraceFactory, jfr, args));
  }

  public static @NotNull SentryProfile convertFromFileStatic(@NotNull Path jfrFilePath)
      throws IOException {
    JfrAsyncProfilerToSentryProfileConverter converter;
    try (JfrReader jfrReader = new JfrReader(jfrFilePath.toString())) {
      Arguments args = new Arguments();
      args.cpu = false;
      args.alloc = false;
      args.threads = true;
      args.lines = true;
      args.dot = true;

      SentryStackTraceFactory stackTraceFactory =
          new SentryStackTraceFactory(Sentry.getGlobalScope().getOptions());
      converter = new JfrAsyncProfilerToSentryProfileConverter(jfrReader, args, stackTraceFactory);
      converter.convert();
    }

    return converter.sentryProfile;
  }

  private class ProfileEventVisitor extends AggregatedEventVisitor {
    private final @NotNull SentryProfile sentryProfile;
    private final @NotNull SentryStackTraceFactory stackTraceFactory;
    private final @NotNull JfrReader jfr;
    private final @NotNull Arguments args;

    public ProfileEventVisitor(
        @NotNull SentryProfile sentryProfile,
        @NotNull SentryStackTraceFactory stackTraceFactory,
        @NotNull JfrReader jfr,
        @NotNull Arguments args) {
      this.sentryProfile = sentryProfile;
      this.stackTraceFactory = stackTraceFactory;
      this.jfr = jfr;
      this.args = args;
    }

    @Override
    public void visit(Event event, long value) {
      StackTrace stackTrace = jfr.stackTraces.get(event.stackTraceId);
      long threadId = resolveThreadId(event.tid);

      if (stackTrace != null) {
        // Process thread metadata if enabled
        if (args.threads) {
          processThreadMetadata(event, threadId);
        }

        // Create and add the sample
        createSample(event, threadId);

        // Build the stack trace from methods
        buildStackTraceAndFrames(stackTrace);
      }
    }

    // Extract thread metadata and add to profile
    private void processThreadMetadata(Event event, long threadId) {
      final String threadName = getPlainThreadName(event.tid);
      sentryProfile
          .getThreadMetadata()
          .computeIfAbsent(
              String.valueOf(threadId),
              k -> {
                SentryThreadMetadata metadata = new SentryThreadMetadata();
                metadata.name = threadName;
                metadata.priority = 0;
                return metadata;
              });
    }

    // Build stack trace from method array
    private void buildStackTraceAndFrames(StackTrace stackTrace) {
      List<Integer> stack = new ArrayList<>();
      int currentFrame = sentryProfile.getFrames().size();

      long[] methods = stackTrace.methods;
      byte[] types = stackTrace.types;
      int[] locations = stackTrace.locations;

      for (int i = 0; i < methods.length; i++) {
        StackTraceElement element = getStackTraceElement(methods[i], types[i], locations[i]);
        if (element.isNativeMethod()) {
          continue;
        }

        SentryStackFrame frame = createStackFrame(element);
        sentryProfile.getFrames().add(frame);

        stack.add(currentFrame);
        currentFrame++;
      }

      sentryProfile.getStacks().add(stack);
    }

    // Create a single stack frame from a stack trace element
    private SentryStackFrame createStackFrame(StackTraceElement element) {
      SentryStackFrame frame = new SentryStackFrame();
      final String classNameWithLambdas = element.getClassName().replace("/", ".");
      frame.setFunction(element.getMethodName());

      // Extract class name without lambda suffix
      String sanitizedClassName = extractSanitizedClassName(classNameWithLambdas);

      // Set module based on package structure
      frame.setModule(extractModuleName(sanitizedClassName, classNameWithLambdas));

      // Determine if frame should be marked as in_app
      if (shouldMarkAsSystemFrame(element, classNameWithLambdas)) {
        frame.setInApp(false);
      } else {
        frame.setInApp(stackTraceFactory.isInApp(sanitizedClassName));
      }

      frame.setLineno(extractLineNumber(element));
      frame.setFilename(classNameWithLambdas);

      return frame;
    }

    // Remove lambda suffix from class name
    private String extractSanitizedClassName(String classNameWithLambdas) {
      int firstDollar = classNameWithLambdas.indexOf('$');
      if (firstDollar != -1) {
        return classNameWithLambdas.substring(0, firstDollar);
      }
      return classNameWithLambdas;
    }

    // Set module name based on package structure
    private @Nullable String extractModuleName(
        String sanitizedClassName, String classNameWithLambdas) {
      if (hasPackageStructure(sanitizedClassName)) {
        return sanitizedClassName;
      } else if (isRegularClassWithoutPackage(classNameWithLambdas)) {
        return "";
      } else {
        return null;
      }
    }

    // Check if the class name has a package structure (contains dots)
    private boolean hasPackageStructure(String className) {
      return className.lastIndexOf('.') > 0;
    }

    // Check if it's a regular class without package (not an array type)
    private boolean isRegularClassWithoutPackage(String className) {
      return !className.startsWith("[");
    }

    // Create sample with timestamp and thread info
    private void createSample(Event event, long threadId) {
      int stackId = sentryProfile.getStacks().size();
      SentrySample sample = new SentrySample();

      // Calculate timestamp from JFR event time
      long nsFromStart =
          (event.time - jfr.chunkStartTicks)
              * JfrAsyncProfilerToSentryProfileConverter.NANOS_PER_SECOND
              / jfr.ticksPerSec;
      long timeNs = jfr.chunkStartNanos + nsFromStart;
      sample.timestamp = DateUtils.nanosToSeconds(timeNs);

      // Set thread ID
      sample.threadId = String.valueOf(threadId);
      sample.stackId = stackId;

      sentryProfile.getSamples().add(sample);
    }

    // Check if the stack frame should be marked as a system frame
    private boolean shouldMarkAsSystemFrame(StackTraceElement element, String className) {
      return element.isNativeMethod() || className.isEmpty();
    }

    // Check if the stack trace element has a valid line number
    private @Nullable Integer extractLineNumber(StackTraceElement element) {
      return element.getLineNumber() != 0 ? element.getLineNumber() : null;
    }

    // Resolve the actual thread ID from the JFR event
    private long resolveThreadId(int eventThreadId) {
      return jfr.threads.get(eventThreadId) != null
          ? jfr.javaThreads.get(eventThreadId)
          : eventThreadId;
    }
  }
}
