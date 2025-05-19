package io.sentry.protocol.jfr.convert;

import io.sentry.protocol.jfr.jfr.JfrReader;
import io.sentry.protocol.jfr.jfr.StackTrace;
import io.sentry.protocol.jfr.jfr.event.Event;
import io.sentry.protocol.profiling.JfrFrame;
import io.sentry.protocol.profiling.JfrProfile;
import io.sentry.protocol.profiling.JfrSample;
import io.sentry.protocol.profiling.JfrToSentryProfileConverter;
import io.sentry.protocol.profiling.ThreadMetadata;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class JfrAsyncProfilerToSentryProfileConverter extends JfrConverter {
  private JfrProfile jfrProfile = new JfrProfile();

  public JfrAsyncProfilerToSentryProfileConverter(JfrReader jfr, Arguments args) {
    super(jfr, args);
  }

  public static void main(String[] args) throws IOException {

    Path jfrPath = Paths.get("/Users/lukasbloder/development/projects/sentry/sentry-java/197d8e97cb514418b15e5578026f39f2.jfr");
    JfrProfile profile = JfrAsyncProfilerToSentryProfileConverter.convertFromFile(jfrPath);
    JfrProfile profile2 = new JfrToSentryProfileConverter().convert(jfrPath);
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

            jfrProfile.threadMetadata.computeIfAbsent(String.valueOf(event.tid), k -> {
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
            JfrFrame frame = new JfrFrame();
            StackTraceElement element = getStackTraceElement(methods[i], types[i], locations[i]);
            final String classNameWithLambdas = element.getClassName().replace("/", ".");
            frame.function = classNameWithLambdas + "." + element.getMethodName();

            int lastDot = classNameWithLambdas.lastIndexOf('.');
            int firstDollar = classNameWithLambdas.indexOf('$');
            if (lastDot > 0 && lastDot > firstDollar) {
              frame.module = classNameWithLambdas.substring(0, lastDot);
            } else if (firstDollar > 0) {
              frame.module = classNameWithLambdas.substring(0, firstDollar);
            } else if (!classNameWithLambdas.startsWith("[")) {
              frame.module = "";
            }
            frame.lineno = (element.getLineNumber() != 0) ? element.getLineNumber() : null;
            frame.filename = classNameWithLambdas;

            jfrProfile.frames.add(frame);
            stack.add(currentFrame);
            currentFrame++;

            System.out.println(element.getMethodName());
            System.out.println(element.getClassName());
            System.out.println(element.getLineNumber());
            System.out.println(element.getFileName());
          }


          long divisor = jfr.ticksPerSec / 1000_000_000L;
          long myTimeStamp = jfr.chunkStartNanos + ((event.time - jfr.chunkStartTicks) / divisor);

          JfrSample sample = new JfrSample();
          Instant instant = Instant.ofEpochSecond(0, myTimeStamp);
          double timestampDouble = instant.getEpochSecond() + instant.getNano() / 1_000_000_000.0;

          sample.timestamp = timestampDouble;
          sample.threadId = String.valueOf(event.tid);
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
