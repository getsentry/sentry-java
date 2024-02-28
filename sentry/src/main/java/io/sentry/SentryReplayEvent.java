package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryReplayEvent extends SentryBaseEvent
    implements JsonUnknown, JsonSerializable {

  public enum ReplayType implements JsonSerializable {
    SESSION,
    BUFFER;

    @Override
    public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
        throws IOException {
      writer.value(name().toLowerCase(Locale.ROOT));
    }

    public static final class Deserializer implements JsonDeserializer<ReplayType> {
      @Override
      public @NotNull ReplayType deserialize(
          final @NotNull ObjectReader reader, final @NotNull ILogger logger) throws Exception {
        return ReplayType.valueOf(reader.nextString().toUpperCase(Locale.ROOT));
      }
    }
  }

  public static final long REPLAY_VIDEO_MAX_SIZE = 10 * 1024 * 1024;
  public static final String REPLAY_EVENT_TYPE = "replay_event";

  private @Nullable File videoFile;
  private @NotNull String type;
  private @NotNull ReplayType replayType;
  private @Nullable SentryId replayId;
  private int segmentId;
  private @NotNull Date timestamp;
  private @Nullable Date replayStartTimestamp;
  private @Nullable List<String> urls;
  private @Nullable List<String> errorIds;
  private @Nullable List<String> traceIds;
  private @Nullable Map<String, Object> unknown;

  public SentryReplayEvent() {
    super();
    this.replayId = new SentryId();
    this.type = REPLAY_EVENT_TYPE;
    this.replayType = ReplayType.SESSION;
    this.errorIds = new ArrayList<>();
    this.traceIds = new ArrayList<>();
    this.urls = new ArrayList<>();
    timestamp = DateUtils.getCurrentDateTime();
  }

  @Nullable
  public File getVideoFile() {
    return videoFile;
  }

  public void setVideoFile(final @Nullable File videoFile) {
    this.videoFile = videoFile;
  }

  @NotNull
  public String getType() {
    return type;
  }

  public void setType(final @NotNull String type) {
    this.type = type;
  }

  @Nullable
  public SentryId getReplayId() {
    return replayId;
  }

  public void setReplayId(final @Nullable SentryId replayId) {
    this.replayId = replayId;
  }

  public int getSegmentId() {
    return segmentId;
  }

  public void setSegmentId(final int segmentId) {
    this.segmentId = segmentId;
  }

  @NotNull
  public Date getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(final @NotNull Date timestamp) {
    this.timestamp = timestamp;
  }

  @Nullable
  public Date getReplayStartTimestamp() {
    return replayStartTimestamp;
  }

  public void setReplayStartTimestamp(final @Nullable Date replayStartTimestamp) {
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

  @NotNull
  public ReplayType getReplayType() {
    return replayType;
  }

  public void setReplayType(final @NotNull ReplayType replayType) {
    this.replayType = replayType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SentryReplayEvent that = (SentryReplayEvent) o;
    return segmentId == that.segmentId
        && Objects.equals(type, that.type)
        && replayType == that.replayType
        && Objects.equals(replayId, that.replayId)
        && Objects.equals(urls, that.urls)
        && Objects.equals(errorIds, that.errorIds)
        && Objects.equals(traceIds, that.traceIds);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, replayType, replayId, segmentId, urls, errorIds, traceIds);
  }

  // region json
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

  @Override
  @SuppressWarnings("JdkObsolete")
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.TYPE).value(type);
    writer.name(JsonKeys.REPLAY_TYPE).value(logger, replayType);
    writer.name(JsonKeys.SEGMENT_ID).value(segmentId);
    writer.name(JsonKeys.TIMESTAMP).value(logger, timestamp);
    if (replayId != null) {
      writer.name(JsonKeys.REPLAY_ID).value(logger, replayId);
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

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull SentryReplayEvent deserialize(
        final @NotNull ObjectReader reader, final @NotNull ILogger logger) throws Exception {

      final SentryBaseEvent.Deserializer baseEventDeserializer = new SentryBaseEvent.Deserializer();

      final SentryReplayEvent replay = new SentryReplayEvent();

      @Nullable Map<String, Object> unknown = null;
      @Nullable String type = null;
      @Nullable ReplayType replayType = null;
      @Nullable SentryId replayId = null;
      @Nullable Integer segmentId = null;
      @Nullable Date timestamp = null;
      @Nullable Date replayStartTimestamp = null;
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
            replayType = reader.nextOrNull(logger, new ReplayType.Deserializer());
            break;
          case JsonKeys.REPLAY_ID:
            replayId = reader.nextOrNull(logger, new SentryId.Deserializer());
            break;
          case JsonKeys.SEGMENT_ID:
            segmentId = reader.nextIntegerOrNull();
            break;
          case JsonKeys.TIMESTAMP:
            timestamp = reader.nextDateOrNull(logger);
            break;
          case JsonKeys.REPLAY_START_TIMESTAMP:
            replayStartTimestamp = reader.nextDateOrNull(logger);
            break;
          case JsonKeys.URLS:
            urls = (List<String>) reader.nextObjectOrNull();
            break;
          case JsonKeys.ERROR_IDS:
            errorIds = (List<String>) reader.nextObjectOrNull();
            break;
          case JsonKeys.TRACE_IDS:
            traceIds = (List<String>) reader.nextObjectOrNull();
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

      if (type != null) {
        replay.setType(type);
      }
      if (replayType != null) {
        replay.setReplayType(replayType);
      }
      if (segmentId != null) {
        replay.setSegmentId(segmentId);
      }
      if (timestamp != null) {
        replay.setTimestamp(timestamp);
      }
      replay.setReplayId(replayId);
      replay.setReplayStartTimestamp(replayStartTimestamp);
      replay.setUrls(urls);
      replay.setErrorIds(errorIds);
      replay.setTraceIds(traceIds);
      replay.setUnknown(unknown);
      return replay;
    }
  }
  // endregion json
}
