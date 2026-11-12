package io.sentry.android.core.anr;

import io.sentry.Attachment;
import io.sentry.JsonObjectWriter;
import io.sentry.NoOpLogger;
import io.sentry.protocol.AnrFlameGraph;
import io.sentry.protocol.AnrFlameGraphNode;
import io.sentry.protocol.AnrFlameGraphTree;
import io.sentry.protocol.DebugImage;
import io.sentry.protocol.DebugMeta;
import io.sentry.protocol.SentryStackFrame;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Creates a flame graph attachment from an {@link AnrProfile}.
 *
 * @see <a href="https://develop.sentry.dev/sdk/telemetry/attachments/#flame-graph">Flame Graph
 *     attachment spec</a>
 */
@ApiStatus.Internal
public final class AnrFlameGraphAttachment {

  static final @NotNull String FILENAME = "flamegraph.json";
  static final @NotNull String CONTENT_TYPE = "application/json";
  static final @NotNull String ATTACHMENT_TYPE = "event.flamegraph";
  static final @NotNull String VERSION = "1";
  static final @NotNull String PLATFORM = "java";
  // The flame graph spec limit, see
  // https://develop.sentry.dev/sdk/telemetry/attachments/#flame-graph
  // The envelope only enforces the type-agnostic maxAttachmentSize.
  static final int MAX_SIZE_BYTES = 10 * 1024 * 1024;

  // Safety net only: JsonObjectWriter applies the depth limit to objects it serializes by
  // reflection, and an AnrFlameGraph holds only JsonSerializables, strings, numbers and lists. The
  // flame graph JSON is at most 4 levels deep.
  private static final int MAX_JSON_DEPTH = 10;
  private static final int NO_PARENT = -1;

  private AnrFlameGraphAttachment() {}

  /**
   * Aggregates the main thread samples of {@code anrProfile} into a single call tree.
   *
   * @param proguardUuid the ProGuard UUID of the build that recorded the profile, added as the only
   *     debug image so the frames can be deobfuscated, or {@code null} to omit the debug meta
   * @return the attachment, or {@code null} if the profile has no frames or the serialized flame
   *     graph exceeds {@link #MAX_SIZE_BYTES}
   */
  public static @Nullable Attachment fromAnrProfile(
      final @NotNull AnrProfile anrProfile, final @Nullable String proguardUuid) {
    final @Nullable AnrFlameGraph flameGraph = toFlameGraph(anrProfile, proguardUuid);
    if (flameGraph == null) {
      return null;
    }

    final @NotNull byte[] bytes;
    try {
      bytes = serialize(flameGraph);
    } catch (IOException e) {
      // only an in-memory stream is written to, which does not throw
      return null;
    }
    if (bytes.length > MAX_SIZE_BYTES) {
      return null;
    }
    return new Attachment(bytes, FILENAME, CONTENT_TYPE, ATTACHMENT_TYPE, false);
  }

  private static @Nullable AnrFlameGraph toFlameGraph(
      final @NotNull AnrProfile anrProfile, final @Nullable String proguardUuid) {
    final @NotNull List<SentryStackFrame> frames = new ArrayList<>();
    final @NotNull Map<String, Integer> frameSignatureToIndex = new HashMap<>();
    final @NotNull List<AnrFlameGraphNode> nodes = new ArrayList<>();
    final @NotNull List<Integer> roots = new ArrayList<>();
    final @NotNull Map<Long, Integer> parentAndFrameToNode = new HashMap<>();

    for (final @NotNull AnrStackTrace anrStackTrace : anrProfile.stacks) {
      final @NotNull StackTraceElement[] stack = anrStackTrace.stack;
      int parentId = NO_PARENT;
      for (int i = stack.length - 1; i >= 0; i--) {
        final @NotNull StackTraceElement element = stack[i];
        final @NotNull String frameSignature = createFrameSignature(element);
        @Nullable Integer frameId = frameSignatureToIndex.get(frameSignature);
        if (frameId == null) {
          frameId = frames.size();
          frames.add(createSentryStackFrame(element));
          frameSignatureToIndex.put(frameSignature, frameId);
        }

        final long key = ((long) parentId << 32) | frameId;
        @Nullable Integer nodeId = parentAndFrameToNode.get(key);
        if (nodeId == null) {
          nodeId = nodes.size();
          nodes.add(new AnrFlameGraphNode(frameId, 0, new ArrayList<>()));
          parentAndFrameToNode.put(key, nodeId);
          if (parentId == NO_PARENT) {
            roots.add(nodeId);
          } else {
            nodes.get(parentId).getChildren().add(nodeId);
          }
        }
        final @NotNull AnrFlameGraphNode node = nodes.get(nodeId);
        node.setSampleCount(node.getSampleCount() + 1);
        parentId = nodeId;
      }
    }

    if (nodes.isEmpty()) {
      return null;
    }

    // the ANR profile only samples the main thread, which is the thread that caused the ANR
    final @NotNull AnrFlameGraphTree tree = new AnrFlameGraphTree(roots);
    tree.setThreadAttributed(true);

    final @NotNull AnrFlameGraph flameGraph =
        new AnrFlameGraph(VERSION, PLATFORM, frames, nodes, Collections.singletonList(tree));
    if (proguardUuid != null) {
      flameGraph.setDebugMeta(createDebugMeta(proguardUuid));
    }
    return flameGraph;
  }

  private static @NotNull String createFrameSignature(final @NotNull StackTraceElement element) {
    return element.getClassName()
        + "#"
        + element.getMethodName()
        + "#"
        + element.getFileName()
        + "#"
        + element.getLineNumber();
  }

  private static @NotNull SentryStackFrame createSentryStackFrame(
      final @NotNull StackTraceElement element) {
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

  private static @NotNull byte[] serialize(final @NotNull AnrFlameGraph flameGraph)
      throws IOException {
    final @NotNull ByteArrayOutputStream stream = new ByteArrayOutputStream();
    try (final @NotNull Writer out = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
      flameGraph.serialize(new JsonObjectWriter(out, MAX_JSON_DEPTH), NoOpLogger.getInstance());
    }
    return stream.toByteArray();
  }

  private static @NotNull DebugMeta createDebugMeta(final @NotNull String proguardUuid) {
    final @NotNull DebugImage debugImage = new DebugImage();
    debugImage.setType(DebugImage.PROGUARD);
    debugImage.setUuid(proguardUuid);
    final @NotNull DebugMeta debugMeta = new DebugMeta();
    debugMeta.setImages(Collections.singletonList(debugImage));
    return debugMeta;
  }
}
