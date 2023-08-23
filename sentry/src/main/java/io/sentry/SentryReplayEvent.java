package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryReplayEvent extends SentryBaseEvent
    implements JsonUnknown, JsonSerializable {

  public static final class JsonKeys {
    public static final String TYPE = "type";
    public static final String REPLAY_TYPE = "replay_type";
    public static final String REPLAY_ID = "replay_id";
    public static final String SEGMENT_ID = "segment_id";
    public static final String TIMESTAMP = "timestamp";
    public static final String REPLAY_START_TIMESTAMP = "replay_start_timestamp";
    public static final String URLS = "urls";
    public static final String ERROR_IDS = "error_ids";
    public static final String TRACE_IDS = "trace_ids";
  }

  private @Nullable String type;
  private @Nullable String replayType;
  private @Nullable SentryId replayId;
  private @Nullable Integer segmentId;
  private @Nullable Double timestamp;
  private @Nullable Double replayStartTimestamp;
  private @Nullable List<String> urls;
  private @Nullable List<String> errorIds;
  private @Nullable List<String> traceIds;
  private @Nullable Map<String, Object> unknown;

  public SentryReplayEvent() {
    super();
    this.replayId = this.getEventId();
    this.type = "replay_event";
    this.replayType = "session";
    this.errorIds = new ArrayList<>();
    this.traceIds = new ArrayList<>();
    this.urls = new ArrayList<>();
  }

  @Nullable
  public String getType() {
    return type;
  }

  public void setType(final @Nullable String type) {
    this.type = type;
  }

  @Nullable
  public SentryId getReplayId() {
    return replayId;
  }

  public void setReplayId(final @Nullable SentryId replayId) {
    this.replayId = replayId;
  }

  @Nullable
  public Integer getSegmentId() {
    return segmentId;
  }

  public void setSegmentId(final @Nullable Integer segmentId) {
    this.segmentId = segmentId;
  }

  @Nullable
  public Double getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(final @Nullable Double timestamp) {
    this.timestamp = timestamp;
  }

  @Nullable
  public Double getReplayStartTimestamp() {
    return replayStartTimestamp;
  }

  public void setReplayStartTimestamp(final @Nullable Double replayStartTimestamp) {
    this.replayStartTimestamp = replayStartTimestamp;
  }

  @Nullable
  public List<String> getUrls() {
    return urls;
  }

  public void setUrls(final @Nullable List<String> urls) {
    this.urls = urls;
  }

  @Nullable
  public List<String> getErrorIds() {
    return errorIds;
  }

  public void setErrorIds(final @Nullable List<String> errorIds) {
    this.errorIds = errorIds;
  }

  @Nullable
  public List<String> getTraceIds() {
    return traceIds;
  }

  public void setTraceIds(final @Nullable List<String> traceIds) {
    this.traceIds = traceIds;
  }

  @Nullable
  public String getReplayType() {
    return replayType;
  }

  public void setReplayType(@Nullable String replayType) {
    this.replayType = replayType;
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (type != null) {
      writer.name(JsonKeys.TYPE).value(type);
    }
    if (replayType != null) {
      writer.name(JsonKeys.REPLAY_TYPE).value(replayType);
    }
    if (replayId != null) {
      writer.name(JsonKeys.REPLAY_ID).value(logger, replayId);
    }
    if (segmentId != null) {
      writer.name(JsonKeys.SEGMENT_ID).value(segmentId);
    }
    if (timestamp != null) {
      writer.name(JsonKeys.TIMESTAMP).value(logger, timestamp);
    }
    if (replayStartTimestamp != null) {
      writer.name(JsonKeys.REPLAY_START_TIMESTAMP).value(logger, replayStartTimestamp);
    }
    if (urls != null) {
      writer.name(JsonKeys.URLS).value(logger, urls);
    }
    if (errorIds != null) {
      writer.name(JsonKeys.ERROR_IDS).value(logger, errorIds);
    }
    if (traceIds != null) {
      writer.name(JsonKeys.TRACE_IDS).value(logger, traceIds);
    }

    new SentryBaseEvent.Serializer().serialize(this, writer, logger);

    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
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

  public static final class Deserializer implements JsonDeserializer<SentryReplayEvent> {

    @Override
    public @NotNull SentryReplayEvent deserialize(
        final @NotNull JsonObjectReader reader, final @NotNull ILogger logger) throws Exception {

      SentryBaseEvent.Deserializer baseEventDeserializer = new SentryBaseEvent.Deserializer();

      final SentryReplayEvent replay = new SentryReplayEvent();

      @Nullable Map<String, Object> unknown = null;
      @Nullable String type = null;
      @Nullable String replayType = null;
      @Nullable SentryId replayId = null;
      @Nullable Integer segmentId = null;
      @Nullable Double timestamp = null;
      @Nullable Double replayStartTimestamp = null;
      @Nullable List<String> urls = null;
      @Nullable List<String> errorIds = null;
      @Nullable List<String> traceIds = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.TYPE:
            type = reader.nextStringOrNull();
            break;
          case JsonKeys.REPLAY_TYPE:
            replayType = reader.nextStringOrNull();
            break;
          case JsonKeys.REPLAY_ID:
            replayId = reader.nextOrNull(logger, new SentryId.Deserializer());
            break;
          case JsonKeys.SEGMENT_ID:
            segmentId = reader.nextIntegerOrNull();
            break;
          case JsonKeys.TIMESTAMP:
            timestamp = nextTimestamp(reader, logger);
            break;
          case JsonKeys.REPLAY_START_TIMESTAMP:
            replayStartTimestamp = nextTimestamp(reader, logger);
            break;
          case JsonKeys.URLS:
            urls = nextStringList(reader);
            break;
          case JsonKeys.ERROR_IDS:
            errorIds = nextStringList(reader);
            break;
          case JsonKeys.TRACE_IDS:
            traceIds = nextStringList(reader);
            break;
          default:
            if (!baseEventDeserializer.deserializeValue(replay, nextName, reader, logger)) {
              if (unknown == null) {
                unknown = new HashMap<>();
              }
              reader.nextUnknown(logger, unknown, nextName);
            }
            break;
        }
      }
      reader.endObject();

      replay.setType(type);
      replay.setReplayType(replayType);
      replay.setReplayId(replayId);
      replay.setSegmentId(segmentId);
      replay.setTimestamp(timestamp);
      replay.setReplayStartTimestamp(replayStartTimestamp);
      replay.setUrls(urls);
      replay.setErrorIds(errorIds);
      replay.setTraceIds(traceIds);
      replay.setUnknown(unknown);
      return replay;
    }

    @Nullable
    private static Double nextTimestamp(
        final @NotNull JsonObjectReader reader, final @NotNull ILogger logger) throws IOException {
      @Nullable Double result;
      try {
        result = reader.nextDoubleOrNull();
      } catch (NumberFormatException e) {
        final Date date = reader.nextDateOrNull(logger);
        result = date != null ? DateUtils.dateToSeconds(date) : null;
      }
      return result;
    }

    @Nullable
    private static List<String> nextStringList(final @NotNull JsonObjectReader reader)
        throws IOException {
      @Nullable List<String> result = null;
      final @Nullable Object data = reader.nextObjectOrNull();
      if (data instanceof List) {
        result = new ArrayList<>(((List<?>) data).size());
        for (Object item : (List<?>) data) {
          if (item instanceof String) {
            result.add((String) item);
          }
        }
      }
      return result;
    }
  }
}
