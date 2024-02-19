package io.sentry.rrweb;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonObjectReader;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectWriter;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RRWebVideoEvent extends RRWebEvent implements JsonUnknown, JsonSerializable {

  public static final String EVENT_TAG = "video";
  public static final String REPLAY_ENCODING = "h264";
  public static final String REPLAY_CONTAINER = "mp4";
  public static final String REPLAY_FRAME_RATE_TYPE_CONSTANT = "constant";
  public static final String REPLAY_FRAME_RATE_TYPE_VARIABLE = "variable";

  private @NotNull String tag;
  private int segmentId;
  private long size;
  private int duration;
  private @NotNull String encoding = REPLAY_ENCODING;
  private @NotNull String container = REPLAY_CONTAINER;
  private int height;
  private int width;
  private int frameCount;
  private @NotNull String frameRateType = REPLAY_FRAME_RATE_TYPE_CONSTANT;
  private int frameRate;
  private int left;
  private int top;
  private @Nullable Map<String, Object> payloadUnknown;
  private @Nullable Map<String, Object> dataUnknown;

  public RRWebVideoEvent() {
    super(RRWebEventType.Custom);
    tag = EVENT_TAG;
  }

  @NotNull
  public String getTag() {
    return tag;
  }

  public void setTag(final @NotNull String tag) {
    this.tag = tag;
  }

  public int getSegmentId() {
    return segmentId;
  }

  public void setSegmentId(final int segmentId) {
    this.segmentId = segmentId;
  }

  public long getSize() {
    return size;
  }

  public void setSize(final long size) {
    this.size = size;
  }

  public int getDuration() {
    return duration;
  }

  public void setDuration(final int duration) {
    this.duration = duration;
  }

  @NotNull
  public String getEncoding() {
    return encoding;
  }

  public void setEncoding(final @NotNull String encoding) {
    this.encoding = encoding;
  }

  @NotNull
  public String getContainer() {
    return container;
  }

  public void setContainer(final @NotNull String container) {
    this.container = container;
  }

  public int getHeight() {
    return height;
  }

  public void setHeight(final int height) {
    this.height = height;
  }

  public int getWidth() {
    return width;
  }

  public void setWidth(final int width) {
    this.width = width;
  }

  public int getFrameCount() {
    return frameCount;
  }

  public void setFrameCount(final int frameCount) {
    this.frameCount = frameCount;
  }

  @NotNull
  public String getFrameRateType() {
    return frameRateType;
  }

  public void setFrameRateType(final @NotNull String frameRateType) {
    this.frameRateType = frameRateType;
  }

  public int getFrameRate() {
    return frameRate;
  }

  public void setFrameRate(final int frameRate) {
    this.frameRate = frameRate;
  }

  public int getLeft() {
    return left;
  }

  public void setLeft(final int left) {
    this.left = left;
  }

  public int getTop() {
    return top;
  }

  public void setTop(final int top) {
    this.top = top;
  }

  public @Nullable Map<String, Object> getPayloadUnknown() {
    return payloadUnknown;
  }

  public void setPayloadUnknown(final @Nullable Map<String, Object> payloadUnknown) {
    this.payloadUnknown = payloadUnknown;
  }

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return dataUnknown;
  }

  @Override
  public void setUnknown(final @Nullable Map<String, Object> unknown) {
    this.dataUnknown = unknown;
  }

  // region json

  // rrweb uses camelCase hence the json keys are in camelCase here
  public static final class JsonKeys {
    public static final String DATA = "data";
    public static final String TAG = "tag";
    public static final String PAYLOAD = "payload";
    public static final String SEGMENT_ID = "segmentId";
    public static final String SIZE = "size";
    public static final String DURATION = "duration";
    public static final String ENCODING = "encoding";
    public static final String CONTAINER = "container";
    public static final String HEIGHT = "height";
    public static final String WIDTH = "width";
    public static final String FRAME_COUNT = "frameCount";
    public static final String FRAME_RATE_TYPE = "frameRateType";
    public static final String FRAME_RATE = "frameRate";
    public static final String LEFT = "left";
    public static final String TOP = "top";
  }

  @Override
  public void serialize(@NotNull ObjectWriter writer, @NotNull ILogger logger) throws IOException {
    writer.beginObject();
    new RRWebEvent.Serializer().serialize(this, writer, logger);
    writer.name(JsonKeys.DATA);
    serializeData(writer, logger);
    writer.endObject();
  }

  private void serializeData(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.TAG).value(tag);
    writer.name(JsonKeys.PAYLOAD);
    serializePayload(writer, logger);
    if (dataUnknown != null) {
      for (String key : dataUnknown.keySet()) {
        Object value = dataUnknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  private void serializePayload(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.SEGMENT_ID).value(segmentId);
    writer.name(JsonKeys.SIZE).value(size);
    writer.name(JsonKeys.DURATION).value(duration);
    writer.name(JsonKeys.ENCODING).value(encoding);
    writer.name(JsonKeys.CONTAINER).value(container);
    writer.name(JsonKeys.HEIGHT).value(height);
    writer.name(JsonKeys.WIDTH).value(width);
    writer.name(JsonKeys.FRAME_COUNT).value(frameCount);
    writer.name(JsonKeys.FRAME_RATE).value(frameRate);
    writer.name(JsonKeys.FRAME_RATE_TYPE).value(frameRateType);
    writer.name(JsonKeys.LEFT).value(left);
    writer.name(JsonKeys.TOP).value(top);
    if (payloadUnknown != null) {
      for (String key : payloadUnknown.keySet()) {
        Object value = payloadUnknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  public static final class Deserializer implements JsonDeserializer<RRWebVideoEvent> {

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull RRWebVideoEvent deserialize(
        @NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();
      RRWebVideoEvent event = new RRWebVideoEvent();
      RRWebEvent.Deserializer baseEventDeserializer = new RRWebEvent.Deserializer();

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case RRWebMetaEvent.JsonKeys.DATA:
            deserializeData(event, reader, logger);
            break;
          default:
            baseEventDeserializer.deserializeValue(event, nextName, reader, logger);
            break;
        }
      }
      reader.endObject();
      return event;
    }

    private void deserializeData(
        final @NotNull RRWebVideoEvent event,
        final @NotNull JsonObjectReader reader,
        final @NotNull ILogger logger)
        throws Exception {
      Map<String, Object> dataUknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.TAG:
            final String tag = reader.nextStringOrNull();
            event.tag = tag == null ? "" : tag;
            break;
          case JsonKeys.PAYLOAD:
            deserializePayload(event, reader, logger);
            break;
          default:
            if (dataUknown == null) {
              dataUknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, dataUknown, nextName);
        }
      }
      event.setUnknown(dataUknown);
      reader.endObject();
    }

    private void deserializePayload(
        final @NotNull RRWebVideoEvent event,
        final @NotNull JsonObjectReader reader,
        final @NotNull ILogger logger)
        throws Exception {
      Map<String, Object> payloadUnknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.SEGMENT_ID:
            event.segmentId = reader.nextInt();
            break;
          case JsonKeys.SIZE:
            final Long size = reader.nextLongOrNull();
            event.size = size == null ? 0 : size;
            break;
          case JsonKeys.DURATION:
            event.duration = reader.nextInt();
            break;
          case JsonKeys.CONTAINER:
            final String container = reader.nextStringOrNull();
            event.container = container == null ? "" : container;
            break;
          case JsonKeys.ENCODING:
            final String encoding = reader.nextStringOrNull();
            event.encoding = encoding == null ? "" : encoding;
            break;
          case JsonKeys.HEIGHT:
            final Integer height = reader.nextIntegerOrNull();
            event.height = height == null ? 0 : height;
            break;
          case JsonKeys.WIDTH:
            final Integer width = reader.nextIntegerOrNull();
            event.width = width == null ? 0 : width;
            break;
          case JsonKeys.FRAME_COUNT:
            final Integer frameCount = reader.nextIntegerOrNull();
            event.frameCount = frameCount == null ? 0 : frameCount;
            break;
          case JsonKeys.FRAME_RATE:
            final Integer frameRate = reader.nextIntegerOrNull();
            event.frameRate = frameRate == null ? 0 : frameRate;
            break;
          case JsonKeys.FRAME_RATE_TYPE:
            final String frameRateType = reader.nextStringOrNull();
            event.frameRateType = frameRateType == null ? "" : frameRateType;
            break;
          case JsonKeys.LEFT:
            final Integer left = reader.nextIntegerOrNull();
            event.left = left == null ? 0 : left;
            break;
          case JsonKeys.TOP:
            final Integer top = reader.nextIntegerOrNull();
            event.top = top == null ? 0 : top;
            break;
          default:
            if (payloadUnknown == null) {
              payloadUnknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, payloadUnknown, nextName);
        }
      }
      event.setUnknown(payloadUnknown);
      reader.endObject();
    }
  }
  // endregion json
}
