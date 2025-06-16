package io.sentry.protocol.jfr.convert;

import io.sentry.Sentry;
import io.sentry.SentryStackTraceFactory;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.jfr.jfr.JfrReader;
import io.sentry.protocol.jfr.jfr.StackTrace;
import io.sentry.protocol.jfr.jfr.event.Event;
import io.sentry.protocol.profiling.JfrFrame;
import io.sentry.protocol.profiling.JfrProfile;
import io.sentry.protocol.profiling.JfrSample;
//import io.sentry.protocol.profiling.JfrToSentryProfileConverter;
import io.sentry.protocol.profiling.ThreadMetadata;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class JfrAsyncProfilerToSentryProfileConverter extends JfrConverter {
  private final JfrProfile jfrProfile = new JfrProfile();

  public JfrAsyncProfilerToSentryProfileConverter(JfrReader jfr, Arguments args) {
    super(jfr, args);
  }

  public static void main(String[] args) throws IOException {

    Path jfrPath = Paths.get("/Users/lukasbloder/development/projects/sentry/sentry-java/ff3cb6b172fc45c4ae16d65fb1fc83fe.jfr");
    JfrProfile profile = JfrAsyncProfilerToSentryProfileConverter.convertFromFile(jfrPath);
//    JfrProfile profile2 = new JfrToSentryProfileConverter().convert(jfrPath);
    System.out.println("Done");
  }

  @Override
  protected void convertChunk() {
    final List<Event> events = new ArrayList<Event>();
    final List<List<Integer>> stacks = new ArrayList<>();

    collector.forEach(new AggregatedEventVisitor() {

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
            if(jfrProfile.threadMetadata == null) {
              jfrProfile.threadMetadata = new HashMap<>();
            }


            long threadIdToUse = jfr.threads.get(event.tid) != null ? jfr.javaThreads.get(event.tid) : event.tid;

            jfrProfile.threadMetadata.computeIfAbsent(String.valueOf(threadIdToUse), k -> {
                ThreadMetadata metadata = new ThreadMetadata();
                metadata.name = getThreadName(event.tid);
                metadata.priority = 0;
                return metadata;
              });
          }

          if(jfrProfile.samples == null) {
            jfrProfile.samples = new ArrayList<>();
          }

          if(jfrProfile.frames == null) {
            jfrProfile.frames = new ArrayList<>();
          }

          List<Integer> stack = new ArrayList<>();
          int currentStack = stacks.size();
          int currentFrame = jfrProfile.frames.size();
          for (int i = 0; i < methods.length; i++) {
//          for (int i = methods.length; --i >= 0; ) {
            SentryStackFrame frame = new SentryStackFrame();
            StackTraceElement element = getStackTraceElement(methods[i], types[i], locations[i]);
            final String classNameWithLambdas = element.getClassName().replace("/", ".");
            frame.setFunction(element.getMethodName());

            int firstDollar = classNameWithLambdas.indexOf('$');
            String sanitizedClassName = classNameWithLambdas;
            if(firstDollar != -1) {
              sanitizedClassName = classNameWithLambdas.substring(0, firstDollar);
            }


            int lastDot = sanitizedClassName.lastIndexOf('.');
            if (lastDot > 0) {
              frame.setModule(sanitizedClassName);
            } else if (!classNameWithLambdas.startsWith("[")) {
              frame.setModule("");
            }

            if(element.isNativeMethod() || classNameWithLambdas.isEmpty()) {
              frame.setInApp(false);
            } else {
              frame.setInApp(new SentryStackTraceFactory(Sentry.getGlobalScope().getOptions()).isInApp(sanitizedClassName));
            }

            frame.setLineno((element.getLineNumber() != 0) ? element.getLineNumber() : null);
            frame.setFilename(classNameWithLambdas);

            jfrProfile.frames.add(frame);
            stack.add(currentFrame);
            currentFrame++;
          }


          long divisor = jfr.ticksPerSec / 1000_000_000L;
          long myTimeStamp = jfr.chunkStartNanos + ((event.time - jfr.chunkStartTicks) / divisor);

          JfrSample sample = new JfrSample();
          Instant instant = Instant.ofEpochSecond(0, myTimeStamp);
          double timestampDouble = instant.getEpochSecond() + instant.getNano() / 1_000_000_000.0;

          sample.timestamp = timestampDouble;
//          sample.threadId = String.valueOf(event.tid);
          sample.threadId = String.valueOf(jfr.threads.get(event.tid) != null ? jfr.javaThreads.get(event.tid) : event.tid);
          sample.stackId = currentStack;
          jfrProfile.samples.add(sample);

          stacks.add(stack);
        }
      }
    });
    jfrProfile.stacks = stacks;
    System.out.println("Samples: " + events.size());
  }

  public static JfrProfile convertFromFile(Path jfrFilePath) throws IOException {
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

    return converter.jfrProfile;
  }
}
