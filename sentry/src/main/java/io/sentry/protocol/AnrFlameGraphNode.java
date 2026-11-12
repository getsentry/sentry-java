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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A node of an {@link AnrFlameGraph} call tree. Its sample count includes the samples of its
 * descendants.
 */
@ApiStatus.Internal
public final class AnrFlameGraphNode implements JsonUnknown, JsonSerializable {

  public static final class JsonKeys {
    public static final String FRAME_ID = "frame_id";
    public static final String SAMPLE_COUNT = "sample_count";
    public static final String CHILDREN = "children";
  }

  private final int frameId;
  private long sampleCount;
  private final @NotNull List<Integer> children;
  private @Nullable Map<String, Object> unknown;

  public AnrFlameGraphNode(
      final int frameId, final long sampleCount, final @NotNull List<Integer> children) {
    this.frameId = frameId;
    this.sampleCount = sampleCount;
    this.children = children;
  }

  public int getFrameId() {
    return frameId;
  }

  public long getSampleCount() {
    return sampleCount;
  }

  public void setSampleCount(final long sampleCount) {
    this.sampleCount = sampleCount;
  }

  public @NotNull List<Integer> getChildren() {
    return children;
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.FRAME_ID).value(frameId);
    writer.name(JsonKeys.SAMPLE_COUNT).value(sampleCount);
    if (!children.isEmpty()) {
      writer.name(JsonKeys.CHILDREN).value(logger, children);
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

  public static final class Deserializer implements JsonDeserializer<AnrFlameGraphNode> {

    @Override
    public @NotNull AnrFlameGraphNode deserialize(
        final @NotNull ObjectReader reader, final @NotNull ILogger logger) throws Exception {
      @Nullable Integer frameId = null;
      @Nullable Long sampleCount = null;
      @Nullable List<Integer> children = null;
      @Nullable Map<String, Object> unknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.FRAME_ID:
            frameId = reader.nextIntegerOrNull();
            break;
          case JsonKeys.SAMPLE_COUNT:
            sampleCount = reader.nextLongOrNull();
            break;
          case JsonKeys.CHILDREN:
            children = reader.nextOrNull(logger, new IntegerListDeserializer());
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

      if (frameId == null) {
        throw missingRequiredFieldException(JsonKeys.FRAME_ID, logger);
      }
      if (sampleCount == null) {
        throw missingRequiredFieldException(JsonKeys.SAMPLE_COUNT, logger);
      }

      final AnrFlameGraphNode node =
          new AnrFlameGraphNode(
              frameId, sampleCount, children != null ? children : new ArrayList<>());
      node.setUnknown(unknown);
      return node;
    }

    private @NotNull Exception missingRequiredFieldException(
        final @NotNull String field, final @NotNull ILogger logger) {
      final String message = "Missing required field \"" + field + "\"";
      final Exception exception = new IllegalStateException(message);
      logger.log(SentryLevel.ERROR, message, exception);
      return exception;
    }
  }

  static final class IntegerListDeserializer implements JsonDeserializer<List<Integer>> {
    @Override
    public @NotNull List<Integer> deserialize(
        final @NotNull ObjectReader reader, final @NotNull ILogger logger) throws IOException {
      final @NotNull List<Integer> result = new ArrayList<>();
      reader.beginArray();
      while (reader.hasNext()) {
        result.add(reader.nextInt());
      }
      reader.endArray();
      return result;
    }
  }
}
