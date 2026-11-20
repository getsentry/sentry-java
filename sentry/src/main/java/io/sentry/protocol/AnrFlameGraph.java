package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.SentryLevel;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The payload of a flame graph attachment, holding the call trees sampled during an ANR.
 *
 * @see <a href="https://develop.sentry.dev/sdk/telemetry/attachments/#flame-graph">Flame Graph
 *     attachment spec</a>
 */
@ApiStatus.Internal
public final class AnrFlameGraph implements JsonUnknown, JsonSerializable {

  public static final class JsonKeys {
    public static final String VERSION = "version";
    public static final String PLATFORM = "platform";
    public static final String FRAMES = "frames";
    public static final String NODES = "nodes";
    public static final String TREES = "trees";
    public static final String DEBUG_META = "debug_meta";
  }

  private final @NotNull String version;
  private final @NotNull String platform;
  private final @NotNull List<SentryStackFrame> frames;
  private final @NotNull List<AnrFlameGraphNode> nodes;
  private final @NotNull List<AnrFlameGraphTree> trees;
  private @Nullable DebugMeta debugMeta;
  private @Nullable Map<String, Object> unknown;

  public AnrFlameGraph(
      final @NotNull String version,
      final @NotNull String platform,
      final @NotNull List<SentryStackFrame> frames,
      final @NotNull List<AnrFlameGraphNode> nodes,
      final @NotNull List<AnrFlameGraphTree> trees) {
    this.version = version;
    this.platform = platform;
    this.frames = frames;
    this.nodes = nodes;
    this.trees = trees;
  }

  public @NotNull String getVersion() {
    return version;
  }

  public @NotNull String getPlatform() {
    return platform;
  }

  public @NotNull List<SentryStackFrame> getFrames() {
    return frames;
  }

  public @NotNull List<AnrFlameGraphNode> getNodes() {
    return nodes;
  }

  public @NotNull List<AnrFlameGraphTree> getTrees() {
    return trees;
  }

  public @Nullable DebugMeta getDebugMeta() {
    return debugMeta;
  }

  public void setDebugMeta(final @Nullable DebugMeta debugMeta) {
    this.debugMeta = debugMeta;
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.VERSION).value(version);
    writer.name(JsonKeys.PLATFORM).value(platform);
    writer.name(JsonKeys.FRAMES).value(logger, frames);
    writer.name(JsonKeys.NODES).value(logger, nodes);
    writer.name(JsonKeys.TREES).value(logger, trees);
    if (debugMeta != null) {
      writer.name(JsonKeys.DEBUG_META).value(logger, debugMeta);
    }
    if (unknown != null) {
      for (final String key : unknown.keySet()) {
        writer.name(key).value(logger, unknown.get(key));
      }
    }
    writer.endObject();
  }

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(final @Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class Deserializer implements JsonDeserializer<AnrFlameGraph> {

    @Override
    public @NotNull AnrFlameGraph deserialize(
        final @NotNull ObjectReader reader, final @NotNull ILogger logger) throws Exception {
      @Nullable String version = null;
      @Nullable String platform = null;
      @Nullable List<SentryStackFrame> frames = null;
      @Nullable List<AnrFlameGraphNode> nodes = null;
      @Nullable List<AnrFlameGraphTree> trees = null;
      @Nullable DebugMeta debugMeta = null;
      @Nullable Map<String, Object> unknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.VERSION:
            version = reader.nextStringOrNull();
            break;
          case JsonKeys.PLATFORM:
            platform = reader.nextStringOrNull();
            break;
          case JsonKeys.FRAMES:
            frames = reader.nextListOrNull(logger, new SentryStackFrame.Deserializer());
            break;
          case JsonKeys.NODES:
            nodes = reader.nextListOrNull(logger, new AnrFlameGraphNode.Deserializer());
            break;
          case JsonKeys.TREES:
            trees = reader.nextListOrNull(logger, new AnrFlameGraphTree.Deserializer());
            break;
          case JsonKeys.DEBUG_META:
            debugMeta = reader.nextOrNull(logger, new DebugMeta.Deserializer());
            break;
          default:
            if (unknown == null) {
              unknown = new HashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      reader.endObject();

      if (version == null) {
        throw missingRequiredFieldException(JsonKeys.VERSION, logger);
      }
      if (platform == null) {
        throw missingRequiredFieldException(JsonKeys.PLATFORM, logger);
      }
      if (frames == null) {
        throw missingRequiredFieldException(JsonKeys.FRAMES, logger);
      }
      if (nodes == null) {
        throw missingRequiredFieldException(JsonKeys.NODES, logger);
      }
      if (trees == null) {
        throw missingRequiredFieldException(JsonKeys.TREES, logger);
      }

      final AnrFlameGraph flameGraph = new AnrFlameGraph(version, platform, frames, nodes, trees);
      flameGraph.setDebugMeta(debugMeta);
      flameGraph.setUnknown(unknown);
      return flameGraph;
    }

    private @NotNull Exception missingRequiredFieldException(
        final @NotNull String field, final @NotNull ILogger logger) {
      final String message = "Missing required field \"" + field + "\"";
      final Exception exception = new IllegalStateException(message);
      logger.log(SentryLevel.ERROR, message, exception);
      return exception;
    }
  }
}
