package io.sentry.android.core.anr;

import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.profiling.SentryProfile;
import io.sentry.protocol.profiling.SentrySample;
import io.sentry.protocol.profiling.SentryThreadMetadata;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Converts a list of {@link AnrStackTrace} objects captured during ANR detection into a {@link
 * SentryProfile} object suitable for profiling telemetry.
 *
 * <p>This converter handles:
 *
 * <ul>
 *   <li>Converting {@link StackTraceElement} to {@link SentryStackFrame}
 *   <li>Deduplicating frames based on their signature
 *   <li>Building stack references using frame indices
 *   <li>Creating samples with timestamps
 *   <li>Populating thread metadata
 * </ul>
 */
@ApiStatus.Internal
public final class StackTraceConverter {

  private static final String MAIN_THREAD_ID = "0";
  private static final String MAIN_THREAD_NAME = "main";

  /**
   * Converts a list of {@link AnrStackTrace} objects to a {@link SentryProfile}.
   *
   * @param anrProfile The ANR Profile
   * @return a populated SentryProfile with deduped frames and samples
   */
  @NotNull
  public static SentryProfile convert(final @NotNull AnrProfile anrProfile) {
    final @NotNull List<AnrStackTrace> anrStackTraces = anrProfile.stacks;

    final @NotNull SentryProfile profile = new SentryProfile();
    final @NotNull List<SentryStackFrame> frames = new ArrayList<>();
    final @NotNull Map<String, Integer> frameSignatureToIndex = new HashMap<>();
    final @NotNull List<List<Integer>> stacks = new ArrayList<>();
    final @NotNull Map<String, Integer> stackSignatureToIndex = new HashMap<>();

    for (final @NotNull AnrStackTrace anrStackTrace : anrStackTraces) {
      final @NotNull StackTraceElement[] stackElements = anrStackTrace.stack;
      final @NotNull List<Integer> frameIndices = new ArrayList<>();
      for (final @NotNull StackTraceElement element : stackElements) {
        final @NotNull String frameSignature = createFrameSignature(element);
        @Nullable Integer frameIndex = frameSignatureToIndex.get(frameSignature);
        if (frameIndex == null) {
          frameIndex = frames.size();
          frames.add(createSentryStackFrame(element));
          frameSignatureToIndex.put(frameSignature, frameIndex);
        }
        frameIndices.add(frameIndex);
      }

      final @NotNull String stackSignature = createStackSignature(frameIndices);
      @Nullable Integer stackIndex = stackSignatureToIndex.get(stackSignature);

      if (stackIndex == null) {
        stackIndex = stacks.size();
        stacks.add(new ArrayList<>(frameIndices));
        stackSignatureToIndex.put(stackSignature, stackIndex);
      }

      final @NotNull SentrySample sample = new SentrySample();
      sample.setTimestamp(anrStackTrace.timestampMs / 1000.0); // Convert ms to seconds
      sample.setStackId(stackIndex);
      sample.setThreadId(MAIN_THREAD_ID);

      profile.getSamples().add(sample);
    }

    profile.setFrames(frames);
    profile.setStacks(stacks);

    final @NotNull SentryThreadMetadata threadMetadata = new SentryThreadMetadata();
    threadMetadata.setName(MAIN_THREAD_NAME);
    threadMetadata.setPriority(Thread.NORM_PRIORITY);

    final @NotNull Map<String, SentryThreadMetadata> threadMetadataMap =
        Collections.singletonMap(MAIN_THREAD_ID, threadMetadata);
    profile.setThreadMetadata(threadMetadataMap);

    return profile;
  }

  /**
   * Creates a unique signature for a StackTraceElement to identify duplicate frames.
   *
   * @param element the stack trace element
   * @return a signature string representing this frame
   */
  @NotNull
  private static String createFrameSignature(@NotNull StackTraceElement element) {
    return element.getClassName()
        + "#"
        + element.getMethodName()
        + "#"
        + element.getFileName()
        + "#"
        + element.getLineNumber();
  }

  /**
   * Creates a unique signature for a stack (list of frame indices) to identify duplicate stacks.
   *
   * @param frameIndices the list of frame indices
   * @return a signature string representing this stack
   */
  @NotNull
  private static String createStackSignature(@NotNull List<Integer> frameIndices) {
    final @NotNull StringBuilder sb = new StringBuilder();
    for (Integer index : frameIndices) {
      if (sb.length() > 0) {
        sb.append(",");
      }
      sb.append(index);
    }
    return sb.toString();
  }

  /**
   * Converts a {@link StackTraceElement} to a {@link SentryStackFrame}.
   *
   * @param element the stack trace element
   * @return a SentryStackFrame populated with available information
   */
  @NotNull
  private static SentryStackFrame createSentryStackFrame(@NotNull StackTraceElement element) {
    final @NotNull SentryStackFrame frame = new SentryStackFrame();
    frame.setFilename(element.getFileName());
    frame.setFunction(element.getMethodName());
    frame.setModule(element.getClassName());
    frame.setLineno(element.getLineNumber() > 0 ? element.getLineNumber() : null);
    if (element.isNativeMethod()) {
      frame.setNative(true);
    }
    return frame;
  }
}
