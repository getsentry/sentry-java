package io.sentry;

import io.sentry.rrweb.RRWebEvent;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ReplayRecording implements JsonUnknown, JsonSerializable {

  public static final class JsonKeys {
    public static final String SEGMENT_ID = "segment_id";
  }

  private @Nullable Integer segmentId;
  private @Nullable List<? extends RRWebEvent> payload;
  private @Nullable Map<String, Object> unknown;

  @Nullable
  public Integer getSegmentId() {
    return segmentId;
  }

  public void setSegmentId(@Nullable Integer segmentId) {
    this.segmentId = segmentId;
  }

  @Nullable
  public List<? extends RRWebEvent> getPayload() {
    return payload;
  }

  public void setPayload(@Nullable List<? extends RRWebEvent> payload) {
    this.payload = payload;
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (segmentId != null) {
      writer.name(JsonKeys.SEGMENT_ID).value(segmentId);
    }

    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }
    writer.endObject();

    // session replay recording format
    // {"segment_id":0}\n{json-serialized-gzipped-rrweb-protocol}

    writer.jsonValue("\n");

    if (payload != null) {
      writer.value(logger, payload);
    }
  }

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class Deserializer implements JsonDeserializer<ReplayRecording> {

    @Override
    public @NotNull ReplayRecording deserialize(
        @NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception {

      final ReplayRecording replay = new ReplayRecording();

      @Nullable Map<String, Object> unknown = null;
      @Nullable Integer segmentId = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.SEGMENT_ID:
            segmentId = reader.nextIntegerOrNull();
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

      replay.setSegmentId(segmentId);
      replay.setUnknown(unknown);
      return replay;
    }
  }
}
