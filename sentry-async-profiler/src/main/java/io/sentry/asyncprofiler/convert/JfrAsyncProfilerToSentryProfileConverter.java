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
  private final @NotNull SentryProfile sentryProfile = new SentryProfile();

  public JfrAsyncProfilerToSentryProfileConverter(JfrReader jfr, Arguments args) {
    super(jfr, args);
  }

  @Override
  protected void convertChunk() {
    final SentryStackTraceFactory stackTraceFactory =
        new SentryStackTraceFactory(Sentry.getGlobalScope().getOptions());

    collector.forEach(
        new AggregatedEventVisitor() {

          @Override
          public void visit(Event event, long value) {
            StackTrace stackTrace = jfr.stackTraces.get(event.stackTraceId);
            long threadId =
                jfr.threads.get(event.tid) != null ? jfr.javaThreads.get(event.tid) : event.tid;

            if (stackTrace != null) {
              // Process thread metadata if enabled
              if (args.threads) {
                processThreadMetadata(event, threadId);
              }

              // Create and add the sample
              createSample(event, threadId);

              // Build the stack trace from methods
              buildStackTraceAndFrames(stackTrace, stackTraceFactory);
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
          private void buildStackTraceAndFrames(
              StackTrace stackTrace, SentryStackTraceFactory stackTraceFactory) {
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

              SentryStackFrame frame = createStackFrame(element, stackTraceFactory);
              sentryProfile.getFrames().add(frame);

              stack.add(currentFrame);
              currentFrame++;
            }

            sentryProfile.getStacks().add(stack);
          }

          // Create a single stack frame from a stack trace element
          private SentryStackFrame createStackFrame(
              StackTraceElement element, SentryStackTraceFactory stackTraceFactory) {
            SentryStackFrame frame = new SentryStackFrame();
            final String classNameWithLambdas = element.getClassName().replace("/", ".");
            frame.setFunction(element.getMethodName());

            // Extract class name without lambda suffix
            String sanitizedClassName = extractSanitizedClassName(classNameWithLambdas);

            // Set module based on package structure
            frame.setModule(extractModuleName(sanitizedClassName, classNameWithLambdas));

            // Determine if frame should be marked as in_app
            if (element.isNativeMethod() || classNameWithLambdas.isEmpty()) {
              frame.setInApp(false);
            } else {
              frame.setInApp(stackTraceFactory.isInApp(sanitizedClassName));
            }

            frame.setLineno((element.getLineNumber() != 0) ? element.getLineNumber() : null);
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
            int lastDot = sanitizedClassName.lastIndexOf('.');
            if (lastDot > 0) {
              return sanitizedClassName;
            } else if (!classNameWithLambdas.startsWith("[")) {
              return "";
            } else {
              return null;
            }
          }

          // Create sample with timestamp and thread info
          private void createSample(Event event, long threadId) {
            int stackId = sentryProfile.getStacks().size();
            SentrySample sample = new SentrySample();

            // Calculate timestamp from JFR event time
            long nsFromStart = (event.time - jfr.chunkStartTicks) * 1_000_000_000 / jfr.ticksPerSec;
            long timeNs = jfr.chunkStartNanos + nsFromStart;
            sample.timestamp = DateUtils.nanosToSeconds(timeNs);

            // Set thread ID
            sample.threadId = String.valueOf(threadId);
            sample.stackId = stackId;

            sentryProfile.getSamples().add(sample);
          }
        });
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

      converter = new JfrAsyncProfilerToSentryProfileConverter(jfrReader, args);
      converter.convert();
    }

    return converter.sentryProfile;
  }
}
