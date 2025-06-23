// package io.sentry.protocol.profiling;
//
// import io.sentry.EnvelopeReader;
// import io.sentry.JsonSerializer;
// import io.sentry.SentryNanotimeDate;
// import io.sentry.SentryOptions;
// import jdk.jfr.consumer.RecordedClass;
// import jdk.jfr.consumer.RecordedEvent;
// import jdk.jfr.consumer.RecordedFrame;
// import jdk.jfr.consumer.RecordedMethod;
// import jdk.jfr.consumer.RecordedStackTrace;
// import jdk.jfr.consumer.RecordedThread;
// import jdk.jfr.consumer.RecordingFile;
//
// import java.io.File;
// import java.io.IOException;
// import java.io.StringWriter;
// import java.nio.file.Path;
// import java.time.Instant;
// import java.util.ArrayList;
// import java.util.Collections;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.Objects;
// import jdk.jfr.consumer.*;
//
// import java.io.IOException;
// import java.nio.file.Files; // For main method example write
// import java.nio.file.Path;
// import java.time.Instant;
// import java.util.ArrayList;
// import java.util.Collections;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.Objects;
// import java.util.concurrent.ConcurrentHashMap;
//
// public final class JfrToSentryProfileConverter {
//
//  // FrameSignature now converts to JfrFrame
//  private static class FrameSignature {
//    String className;
//    String methodName;
//    String descriptor;
//    String sourceFile;
//    int lineNumber;
//
//    FrameSignature(RecordedFrame rf) {
//      RecordedMethod rm = rf.getMethod();
//      if (rm != null) {
//        RecordedClass type = rm.getType();
//        this.className = type != null ? type.getName() : "[unknown_class]";
//        this.methodName = rm.getName();
//        this.descriptor = rm.getDescriptor();
//      } else {
//        this.className = "[unknown_class]";
//        this.methodName = "[unknown_method]";
//        this.descriptor = "()V";
//      }
//
//      String fileNameFromClass = null;
//      if (rf.isJavaFrame() && rm != null && rm.getType() != null) {
//        try { fileNameFromClass = rm.getType().getString("sourceFileName"); }
//        catch (Exception e) { fileNameFromClass = null; }
//      }
//
//      if (fileNameFromClass != null && !fileNameFromClass.isEmpty()) {
//        this.sourceFile = fileNameFromClass;
//      } else if (rf.isJavaFrame() && this.className != null && !this.className.startsWith("[")) {
//        int lastDot = this.className.lastIndexOf('.');
//        String simpleClassName = lastDot > 0 ? this.className.substring(lastDot + 1) :
// this.className;
//        int firstDollar = simpleClassName.indexOf('$');
//        if (firstDollar > 0) simpleClassName = simpleClassName.substring(0, firstDollar);
//        this.sourceFile = simpleClassName + ".java";
//      } else {
//        this.sourceFile = "[unknown_source]";
//      }
//      if (!rf.isJavaFrame()) this.sourceFile = "[native]";
//
//      this.lineNumber = rf.getInt("lineNumber");
//      if (this.lineNumber < 0) this.lineNumber = 0;
//    }
//
//    @Override
//    public boolean equals(Object o) {
//      if (this == o) return true;
//      if (!(o instanceof FrameSignature)) return false;
//      FrameSignature that = (FrameSignature) o;
//      return lineNumber == that.lineNumber &&
//        Objects.equals(className, that.className) &&
//        Objects.equals(methodName, that.methodName) &&
//        Objects.equals(descriptor, that.descriptor) &&
//        Objects.equals(sourceFile, that.sourceFile);
//    }
//
//    @Override
//    public int hashCode() {
//      return Objects.hash(className, methodName, descriptor, sourceFile, lineNumber);
//    }
//
//    // **** Method now returns JfrFrame ****
//    JfrFrame toSentryFrame() {
//      JfrFrame frame = new JfrFrame(); // Create JfrFrame instance
//      frame.function = this.className + "." + this.methodName;
//
//      int lastDot = this.className.lastIndexOf('.');
//      if (lastDot > 0) {
//        frame.module = this.className.substring(0, lastDot);
//      } else if (!this.className.startsWith("[")) {
//        frame.module = "";
//      }
//
//      frame.filename = this.sourceFile;
//
//      if (this.lineNumber > 0) frame.lineno = this.lineNumber;
//      else frame.lineno = null;
//
//      if ("[native]".equals(this.sourceFile)) {
//        frame.function = "[native_code]";
//        frame.module = null;
//        frame.filename = null;
//        frame.lineno = null;
//      }
//      return frame; // Return JfrFrame
//    }
//  }
//  // --- End of FrameSignature ---
//
//  private final Map<Long, String> threadNamesByOSId = new ConcurrentHashMap<>();
//
//  public JfrProfile convert(Path jfrFilePath) throws IOException {
//
//    // **** Use renamed classes for lists ****
//    List<JfrSample> samples = new ArrayList<>();
//    List<List<Integer>> stacks = new ArrayList<>();
//    List<JfrFrame> frames = new ArrayList<>();
//    Map<String, ThreadMetadata> threadMetadata = new ConcurrentHashMap<>();
//
//    Map<List<Integer>, Integer> stackIdMap = new HashMap<>();
//    Map<FrameSignature, Integer> frameIdMap = new HashMap<>();
//
//    long eventCount = 0;
//    long sampleCount = 0;
//    long threadsFoundDirectly = 0;
//    long threadsFoundInMetadata = 0;
//
//    // --- Pre-pass for Thread Metadata ---
//    System.out.println("Pre-scanning for thread metadata...");
//    try (RecordingFile recordingFile = new RecordingFile(jfrFilePath)) {
//      while (recordingFile.hasMoreEvents()) {
//        RecordedEvent event = recordingFile.readEvent();
//        String eventName = event.getEventType().getName();
//        if ("jdk.ThreadStart".equals(eventName)) {
//          RecordedThread thread = null;
//          try { thread = event.getThread("thread"); } catch(Exception e) {
//            // Handle exception if needed
//          }
//          RecordedThread eventThread = null;
//          try { eventThread = event.getThread("eventThread"); } catch(Exception e){
//            // Handle exception if needed
//          }
//
//          if (thread != null) {
//            long osId = thread.getOSThreadId();
//            String name = thread.getJavaName() != null ? thread.getJavaName() :
// thread.getOSName();
//            if (osId > 0 && name != null) threadNamesByOSId.put(osId, name);
//          }
//          if (eventThread != null) {
//            long osId = eventThread.getOSThreadId();
//            String name = eventThread.getJavaName() != null ? eventThread.getJavaName() :
// eventThread.getOSName();
//            if (osId > 0 && name != null) threadNamesByOSId.put(osId, name);
//          }
//          try {
//            long osId = event.getLong("osThreadId");
//            String name = event.getString("threadName");
//            if (osId > 0 && name != null) threadNamesByOSId.put(osId, name);
//          } catch (Exception e) {/* ignore */}
//
//        } else if ("jdk.JavaThreadStatistics".equals(eventName)) {
//          try {
//            long osId = event.getLong("osThreadId");
//            String name = event.getString("javaThreadName");
//            if (osId > 0 && name != null) threadNamesByOSId.putIfAbsent(osId, name);
//          } catch (Exception e) {/* ignore */}
//        }
//      }
//    }
//    System.out.println("Found " + threadNamesByOSId.size() + " thread names during pre-scan.");
//
//    // --- Main Processing Pass ---
//    System.out.println("Processing execution samples...");
//    try (RecordingFile recordingFile = new RecordingFile(jfrFilePath)) {
//      while (recordingFile.hasMoreEvents()) {
//        RecordedEvent event = recordingFile.readEvent();
//        eventCount++;
//
//        if ("jdk.ExecutionSample".equals(event.getEventType().getName())) {
//          sampleCount++;
//          Instant timestamp = event.getStartTime();
//          RecordedStackTrace stackTrace = event.getStackTrace();
//
//          if (stackTrace == null) {
//            System.err.println("Skipping sample due to missing stacktrace at " + timestamp);
//            continue;
//          }
//
//          // --- Get Thread ID ---
//          long osThreadId = -1;
//          String threadName = null;
//          RecordedThread recordedThread = null;
//          try { recordedThread = event.getThread(); } catch (Exception e) {
//            // Handle exception if needed
//          }
//
//          if (recordedThread != null) {
//            osThreadId = recordedThread.getOSThreadId();
//            threadsFoundDirectly++;
//          } else {
//            try {
//              if (event.hasField("sampledThread")) {
//                RecordedThread eventThreadRef = event.getValue("sampledThread");
//                threadName = eventThreadRef.getJavaName() != null ? eventThreadRef.getJavaName() :
// eventThreadRef.getOSName();
//                if (eventThreadRef != null) osThreadId = eventThreadRef.getOSThreadId();
//              }
////              if (osThreadId <= 0 && event.hasField("tid")) osThreadId = event.getLong("tid");
////              if (osThreadId <= 0 && event.hasField("osThreadId")) osThreadId =
// event.getLong("osThreadId");
////              if (osThreadId <= 0) {
////                System.err.println("WARN: Could not determine OS Thread ID for sample at " +
// timestamp + ". Skipping.");
////                continue;
////              }
//              threadsFoundInMetadata++;
//            } catch (Exception e) {
//              System.err.println("WARN: Error accessing thread ID field for sample at " +
// timestamp + ". Skipping. Error: " + e.getMessage());
//              continue;
//            }
//          }
//
//          if (osThreadId <= 0) {
//            System.err.println("WARN: Invalid OS Thread ID (<= 0) for sample at " + timestamp + ".
// Skipping.");
//            continue;
//          }
//          String threadIdStr = String.valueOf(osThreadId);
////          final long intermediateThreadId = osThreadId;
//          final String intermediateThreadName = threadName;
//          // --- Thread Metadata ---
//          threadMetadata.computeIfAbsent(threadIdStr, tid -> {
//            ThreadMetadata meta = new ThreadMetadata();
//            meta.name =
// intermediateThreadName;//threadNamesByOSId.getOrDefault(intermediateThreadId, "Thread " + tid);
//            // meta.priority = ...; // Priority logic if needed
//            return meta;
//          });
//
//          // --- Stack Trace Processing (Frames and Stacks) ---
//          List<RecordedFrame> jfrFrames = stackTrace.getFrames();
//          List<Integer> currentFrameIds = new ArrayList<>(jfrFrames.size());
//
//          for (RecordedFrame jfrFrame : jfrFrames) {
//            FrameSignature sig = new FrameSignature(jfrFrame);
//            int frameId = frameIdMap.computeIfAbsent(sig, fSig -> {
//              // **** Get JfrFrame from signature ****
//              JfrFrame newFrame = fSig.toSentryFrame();
//              frames.add(newFrame); // Add to List<JfrFrame>
//              return frames.size() - 1;
//            });
//            currentFrameIds.add(frameId);
//          }
//
//          Collections.reverse(currentFrameIds);
//
//          int stackId = stackIdMap.computeIfAbsent(currentFrameIds, frameIds -> {
//            stacks.add(new ArrayList<>(frameIds));
//            return stacks.size() - 1;
//          });
//
//          // --- Create Sentry Sample ---
//          // **** Create instance of JfrSample ****
//          JfrSample sample = new JfrSample();
//          sample.timestamp = timestamp.getEpochSecond() + timestamp.getNano() / 1_000_000_000.0;
//          sample.stackId = stackId;
//          sample.threadId = threadIdStr;
//          samples.add(sample); // Add to List<JfrSample>
//        }
//      }
//    }
//
//    System.out.println("Processed " + eventCount + " JFR events.");
//    System.out.println("Created " + sampleCount + " Sentry samples.");
//    System.out.println("Threads found via getThread(): " + threadsFoundDirectly);
//    System.out.println("Threads found via field fallback: " + threadsFoundInMetadata);
//    System.out.println("Discovered " + frames.size() + " unique frames.");
//    System.out.println("Discovered " + stacks.size() + " unique stacks.");
//    System.out.println("Discovered " + threadMetadata.size() + " unique threads.");
//
//    // --- Assemble final structure ---
//    // **** Create instance of JfrProfile ****
//    JfrProfile profile = new JfrProfile();
//    profile.samples = samples;
//    profile.stacks = stacks;
//    profile.frames = frames;
//    profile.threadMetadata = new HashMap<>(threadMetadata); // Convert map for final object
//
//    return profile;
//
//  }
//
//  // --- Example Usage (main method remains the same) ---
//  public static void main(String[] args) {
//    if (args.length < 1) {
//      System.err.println("Usage: java JfrToSentryProfileConverter <path/to/your.jfr>");
//      System.exit(1);
//    }
//
//    Path jfrPath = new File(args[0]).toPath();
//    JfrToSentryProfileConverter converter = new JfrToSentryProfileConverter();
//
//    SentryOptions options = new SentryOptions();
//    JsonSerializer serializer = new JsonSerializer(options);
//    options.setSerializer(serializer);
//    options.setEnvelopeReader(new EnvelopeReader(serializer));
//
//    try {
//      System.out.println("Parsing JFR file: " + jfrPath.toAbsolutePath());
//      JfrProfile jfrProfile = converter.convert(jfrPath);
//      StringWriter writer = new StringWriter();
//      serializer.serialize(jfrProfile, writer);
//      String sentryJson = writer.toString();
//      System.out.println("\n--- Sentry Profile JSON ---");
//      System.out.println(sentryJson);
//      System.out.println("--- End Sentry Profile JSON ---");
//
//      // Optionally write to a file:
//      // Files.writeString(Path.of("sentry_profile.json"), sentryJson);
//      // System.out.println("Output written to sentry_profile.json");
//
//    } catch (IOException e) {
//      System.err.println("Error processing JFR file: " + e.getMessage());
//      e.printStackTrace();
//      System.exit(1);
//    } catch (Exception e) {
//      System.err.println("An unexpected error occurred: " + e.getMessage());
//      e.printStackTrace();
//      System.exit(1);
//    }
//  }
// }
