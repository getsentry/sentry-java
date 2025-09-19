package io.sentry.asyncprofiler.convert;

import io.sentry.DateUtils;
import io.sentry.Sentry;
import io.sentry.SentryStackTraceFactory;
import io.sentry.asyncprofiler.vendor.asyncprofiler.convert.Arguments;
import io.sentry.asyncprofiler.vendor.asyncprofiler.convert.JfrConverter;
import io.sentry.asyncprofiler.vendor.asyncprofiler.jfr.JfrReader;
import io.sentry.asyncprofiler.vendor.asyncprofiler.jfr.StackTrace;
import io.sentry.asyncprofiler.vendor.asyncprofiler.jfr.event.Event;
import io.sentry.asyncprofiler.vendor.asyncprofiler.jfr.event.EventCollector;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.profiling.SentryProfile;
import io.sentry.protocol.profiling.SentrySample;
import io.sentry.protocol.profiling.SentryThreadMetadata;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JfrAsyncProfilerToSentryProfileConverter extends JfrConverter {
  private static final double NANOS_PER_SECOND = 1_000_000_000.0;

  private final @NotNull SentryProfile sentryProfile = new SentryProfile();
  private final @NotNull SentryStackTraceFactory stackTraceFactory;
  private final @NotNull Map<SentryStackFrame, Integer> frameDeduplicationMap = new HashMap<>();
  private final @NotNull Map<List<Integer>, Integer> stackDeduplicationMap = new HashMap<>();

  public JfrAsyncProfilerToSentryProfileConverter(
      JfrReader jfr, Arguments args, @NotNull SentryStackTraceFactory stackTraceFactory) {
    super(jfr, args);
    this.stackTraceFactory = stackTraceFactory;
  }

  @Override
  protected void convertChunk() {
    collector.forEach(new ProfileEventVisitor(sentryProfile, stackTraceFactory, jfr, args));
  }

  @Override
  protected EventCollector createCollector(Arguments args) {
    return new NonAggregatingEventCollector();
  }

  public static @NotNull SentryProfile convertFromFileStatic(@NotNull Path jfrFilePath)
      throws IOException {
    JfrAsyncProfilerToSentryProfileConverter converter;
    try (JfrReader jfrReader = new JfrReader(jfrFilePath.toString())) {
      Arguments args = new Arguments();
      args.cpu = false;
      args.wall = true;
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

  private class ProfileEventVisitor implements EventCollector.Visitor {
    private final @NotNull SentryProfile sentryProfile;
    private final @NotNull SentryStackTraceFactory stackTraceFactory;
    private final @NotNull JfrReader jfr;
    private final @NotNull Arguments args;
    private final double ticksPerNanosecond;

    public ProfileEventVisitor(
        @NotNull SentryProfile sentryProfile,
        @NotNull SentryStackTraceFactory stackTraceFactory,
        @NotNull JfrReader jfr,
        @NotNull Arguments args) {
      this.sentryProfile = sentryProfile;
      this.stackTraceFactory = stackTraceFactory;
      this.jfr = jfr;
      this.args = args;
      ticksPerNanosecond = jfr.ticksPerSec / NANOS_PER_SECOND;
    }

    @Override
    public void visit(Event event, long samples, long value) {
      StackTrace stackTrace = jfr.stackTraces.get(event.stackTraceId);
      long threadId = resolveThreadId(event.tid);

      if (stackTrace != null) {
        if (args.threads) {
          processThreadMetadata(event, threadId);
        }

        processSampleWithStack(event, threadId, stackTrace);
      }
    }

    private long resolveThreadId(int eventThreadId) {
      return jfr.threads.get(eventThreadId) != null
          ? jfr.javaThreads.get(eventThreadId)
          : eventThreadId;
    }

    private void processThreadMetadata(Event event, long threadId) {
      final String threadName = getPlainThreadName(event.tid);
      sentryProfile
          .getThreadMetadata()
          .computeIfAbsent(
              String.valueOf(threadId),
              k -> {
                SentryThreadMetadata metadata = new SentryThreadMetadata();
                metadata.setName(threadName);
                metadata.setPriority(0); // Default priority
                return metadata;
              });
    }

    private void processSampleWithStack(Event event, long threadId, StackTrace stackTrace) {
      int stackIndex = addStackTrace(stackTrace);

      SentrySample sample = new SentrySample();
      sample.setTimestamp(calculateTimestamp(event));
      sample.setThreadId(String.valueOf(threadId));
      sample.setStackId(stackIndex);

      sentryProfile.getSamples().add(sample);
    }

    private double calculateTimestamp(Event event) {
      long nanosFromStart = (long) ((event.time - jfr.chunkStartTicks) / ticksPerNanosecond);

      long timeNs = jfr.chunkStartNanos + nanosFromStart;

      return DateUtils.nanosToSeconds(timeNs);
    }

    private int addStackTrace(StackTrace stackTrace) {
      List<Integer> callStack = createFramesAndCallStack(stackTrace);

      Integer existingIndex = stackDeduplicationMap.get(callStack);
      if (existingIndex != null) {
        return existingIndex;
      }

      int stackIndex = sentryProfile.getStacks().size();
      sentryProfile.getStacks().add(callStack);
      stackDeduplicationMap.put(callStack, stackIndex);
      return stackIndex;
    }

    private List<Integer> createFramesAndCallStack(StackTrace stackTrace) {
      List<Integer> callStack = new ArrayList<>();

      long[] methods = stackTrace.methods;
      byte[] types = stackTrace.types;
      int[] locations = stackTrace.locations;

      for (int i = 0; i < methods.length; i++) {
        StackTraceElement element = getStackTraceElement(methods[i], types[i], locations[i]);
        if (element.isNativeMethod() || isNativeFrame(types[i])) {
          continue;
        }

        SentryStackFrame frame = createStackFrame(element);
        int frameIndex = getOrAddFrame(frame);
        callStack.add(frameIndex);
      }

      return callStack;
    }

    // Get existing frame index or add new frame and return its index
    private int getOrAddFrame(SentryStackFrame frame) {
      Integer existingIndex = frameDeduplicationMap.get(frame);

      if (existingIndex != null) {
        return existingIndex;
      }

      int newIndex = sentryProfile.getFrames().size();
      sentryProfile.getFrames().add(frame);
      frameDeduplicationMap.put(frame, newIndex);
      return newIndex;
    }

    private SentryStackFrame createStackFrame(StackTraceElement element) {
      SentryStackFrame frame = new SentryStackFrame();
      final String classNameWithLambdas = element.getClassName().replace("/", ".");
      frame.setFunction(element.getMethodName());

      String sanitizedClassName = extractSanitizedClassName(classNameWithLambdas);
      frame.setModule(extractModuleName(sanitizedClassName, classNameWithLambdas));

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

    // TODO: test difference between null and empty string for module
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

    private boolean hasPackageStructure(String className) {
      return className.lastIndexOf('.') > 0;
    }

    private boolean isRegularClassWithoutPackage(String className) {
      return !className.startsWith("[");
    }

    private boolean shouldMarkAsSystemFrame(StackTraceElement element, String className) {
      return element.isNativeMethod() || className.isEmpty();
    }

    private @Nullable Integer extractLineNumber(StackTraceElement element) {
      return element.getLineNumber() != 0 ? element.getLineNumber() : null;
    }
  }
}
