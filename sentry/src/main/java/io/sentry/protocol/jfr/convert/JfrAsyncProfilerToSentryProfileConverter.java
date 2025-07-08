package io.sentry.protocol.jfr.convert;

import io.sentry.Sentry;
import io.sentry.SentryStackTraceFactory;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.jfr.jfr.JfrReader;
import io.sentry.protocol.jfr.jfr.StackTrace;
import io.sentry.protocol.jfr.jfr.event.Event;
import io.sentry.protocol.profiling.SentryProfile;
import io.sentry.protocol.profiling.JfrSample;
// import io.sentry.protocol.profiling.JfrToSentryProfileConverter;
import io.sentry.protocol.profiling.ThreadMetadata;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public final class JfrAsyncProfilerToSentryProfileConverter extends JfrConverter {
  private final @NotNull SentryProfile sentryProfile = new SentryProfile();

  public JfrAsyncProfilerToSentryProfileConverter(JfrReader jfr, Arguments args) {
    super(jfr, args);
  }

  public static void main(String[] args) throws IOException {

    Path jfrPath =
        Paths.get(
            "/Users/lukasbloder/development/projects/sentry/sentry-java/ff3cb6b172fc45c4ae16d65fb1fc83fe.jfr");
    SentryProfile profile = JfrAsyncProfilerToSentryProfileConverter.convertFromFile(jfrPath);
    //    JfrProfile profile2 = new JfrToSentryProfileConverter().convert(jfrPath);
    System.out.println(profile.frames);
    System.out.println("Done");
  }

  @Override
  protected void convertChunk() {
    final List<Event> events = new ArrayList<Event>();
    final List<List<Integer>> stacks = new ArrayList<>();

    collector.forEach(
        new AggregatedEventVisitor() {

          @Override
          public void visit(Event event, long value) {
            events.add(event);
            System.out.println(event);
            StackTrace stackTrace = jfr.stackTraces.get(event.stackTraceId);

            if (stackTrace != null) {
              Arguments args = JfrAsyncProfilerToSentryProfileConverter.this.args;
              long[] methods = stackTrace.methods;
              byte[] types = stackTrace.types;
              int[] locations = stackTrace.locations;

              if (args.threads) {
                if (sentryProfile.threadMetadata == null) {
                  sentryProfile.threadMetadata = new HashMap<>();
                }

                long threadIdToUse =
                    jfr.threads.get(event.tid) != null ? jfr.javaThreads.get(event.tid) : event.tid;

                if (sentryProfile.threadMetadata != null) {
                  final String threadName = getThreadName(event.tid);
                  //                  if(threadName.startsWith("AsyncProfiler-")) {
                  //                    // AsyncProfiler threads are not useful for profiling, so we
                  // skip them
                  //                    return;
                  //                  }
                  sentryProfile.threadMetadata.computeIfAbsent(
                      String.valueOf(threadIdToUse),
                      k -> {
                        ThreadMetadata metadata = new ThreadMetadata();
                        metadata.name = threadName;
                        metadata.priority = 0;
                        return metadata;
                      });
                }
              }

              if (sentryProfile.samples == null) {
                sentryProfile.samples = new ArrayList<>();
              }

              if (sentryProfile.frames == null) {
                sentryProfile.frames = new ArrayList<>();
              }

              List<Integer> stack = new ArrayList<>();
              int currentStack = stacks.size();
              int currentFrame = sentryProfile.frames != null ? sentryProfile.frames.size() : 0;
              for (int i = 0; i < methods.length; i++) {
                //          for (int i = methods.length; --i >= 0; ) {
                SentryStackFrame frame = new SentryStackFrame();
                StackTraceElement element =
                    getStackTraceElement(methods[i], types[i], locations[i]);
                if (element.isNativeMethod()) {
                  continue;
                }

                final String classNameWithLambdas = element.getClassName().replace("/", ".");
                frame.setFunction(element.getMethodName());

                int firstDollar = classNameWithLambdas.indexOf('$');
                String sanitizedClassName = classNameWithLambdas;
                if (firstDollar != -1) {
                  sanitizedClassName = classNameWithLambdas.substring(0, firstDollar);
                }

                int lastDot = sanitizedClassName.lastIndexOf('.');
                if (lastDot > 0) {
                  frame.setModule(sanitizedClassName);
                } else if (!classNameWithLambdas.startsWith("[")) {
                  frame.setModule("");
                }

                if (element.isNativeMethod() || classNameWithLambdas.isEmpty()) {
                  frame.setInApp(false);
                } else {
                  frame.setInApp(
                      new SentryStackTraceFactory(Sentry.getGlobalScope().getOptions())
                          .isInApp(sanitizedClassName));
                }

                frame.setLineno((element.getLineNumber() != 0) ? element.getLineNumber() : null);
                frame.setFilename(classNameWithLambdas);

                if (sentryProfile.frames != null) {
                  sentryProfile.frames.add(frame);
                }
                stack.add(currentFrame);
                currentFrame++;
              }

              long divisor = jfr.ticksPerSec / 1000_000_000L;
              long myTimeStamp =
                  jfr.chunkStartNanos + ((event.time - jfr.chunkStartTicks) / divisor);

              JfrSample sample = new JfrSample();
              Instant instant = Instant.ofEpochSecond(0, myTimeStamp);
              double timestampDouble =
                  instant.getEpochSecond() + instant.getNano() / 1_000_000_000.0;

              sample.timestamp = timestampDouble;
              //          sample.threadId = String.valueOf(event.tid);
              sample.threadId =
                  String.valueOf(
                      jfr.threads.get(event.tid) != null
                          ? jfr.javaThreads.get(event.tid)
                          : event.tid);
              sample.stackId = currentStack;
              if (sentryProfile.samples != null) {
                sentryProfile.samples.add(sample);
              }

              stacks.add(stack);
            }
          }
        });
    sentryProfile.stacks = stacks;
    System.out.println("Samples: " + events.size());
  }

  public static @NotNull SentryProfile convertFromFile(@NotNull Path jfrFilePath) throws IOException {
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
